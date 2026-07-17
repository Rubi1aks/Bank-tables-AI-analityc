from typing import List
from gigachat import GigaChat
from gigachat.models import Chat, Messages
from dotenv import load_dotenv
import os
import asyncio
import json
import re

load_dotenv()

AUTH_TOKEN = os.getenv("AUTH_TOKEN")


def gigachat_text(data: str) -> str:
    if not AUTH_TOKEN:
        return "Ошибка: токен GigaChat не найден"

    prompt = "Ты - ведущий FinTech-аналитик. Твоя задача - провести экспресс-анализ " \
             "предоставленных данных. Пиши максимально кратко, тезисно и строго по делу. " \
             "Только сухие факты и цифры. Максимум 3-4 предложения. " \
             "Вот данные: "

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    try:
        with GigaChat(credentials=AUTH_TOKEN, verify_ssl_certs=False) as giga:
            payload = Chat(
                model="GigaChat-Max",
                messages=[Messages(role="user", content=prompt + data)],
                temperature=0.4,
                max_tokens=1000
            )
            response = giga.chat(payload)
            return response.choices[0].message.content.strip()
    except Exception as e:
        return f"Ошибка GigaChat: {str(e)}"


_REFUSAL_MARKERS = (
    "не обладают собственным мнением",
    "обобщением информации",
    "разговоры на",
    "как языковая модель",
    "не могу обсуждать",
    "не могу помочь",
    "давайте сменим тему",
    "не буду",
    "избегаю",
    "чувствительн",
)

_SENSITIVE_WORDS = (
    "трамп", "байден", "путин", "зеленск", "иран", "израил", "украин",
    "всу", "нато", "ракет", "patriot", "патриот", "обстрел", "удар",
    "фронт", "мобилизац", "война", "войну", "войны", "теракт", "митинг",
    "протест", "санкц",
)


def _looks_like_refusal(text: str) -> bool:
    low = (text or "").lower()
    return any(m in low for m in _REFUSAL_MARKERS)


def _salvage_objects(text: str) -> list:
    objs = []
    depth = 0
    start = None
    in_str = False
    esc = False
    for i, ch in enumerate(text):
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch == "{":
            if depth == 0:
                start = i
            depth += 1
        elif ch == "}":
            if depth > 0:
                depth -= 1
                if depth == 0 and start is not None:
                    try:
                        objs.append(json.loads(text[start:i + 1]))
                    except json.JSONDecodeError:
                        pass
                    start = None
    return objs


def _parse_news_json(text: str):
    text = (text or "").strip()
    if "```json" in text:
        text = text.split("```json")[1].split("```")[0]
    elif "```" in text:
        text = text.split("```")[1].split("```")[0]
    text = text.strip()
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        m = re.search(r"\[.*\]", text, re.DOTALL)
        if m:
            try:
                return json.loads(m.group(0))
            except json.JSONDecodeError:
                pass
        salvaged = _salvage_objects(text)
        if salvaged:
            return salvaged
    return None


def _strip_sensitive_lines(news_text: str) -> str:
    kept = [block for block in news_text.split("\n\n")
            if not any(w in block.lower() for w in _SENSITIVE_WORDS)]
    return "\n\n".join(kept)


def _build_analyze_system_prompt(subject: str, indicators: List[str] = None) -> str:
    keywords = ", ".join(indicators) if indicators else "не заданы"
    region = subject if subject else "не задан"
    return f"""Ты — система классификации деловых новостей. Ты не высказываешь мнений и не комментируешь события — ты только извлекаешь данные и присваиваешь метки.

Задача: из присланного списка новостей выбери от 3 до 5 самых значимых для бизнес-показателей и верни их строго как JSON-массив.

Правила отбора:
- Регион приоритета: {region}. Если задан — приоритет новостям про него.
- Показатели пользователя: {keywords}. Приоритет новостям, связанным с ними.
- Острые/политические/военные темы не оценивай и не комментируй. Если такая новость не связана с показателями или регионом — просто не включай её в выборку.
- Для каждой выбранной новости: очень краткое резюме (1–2 предложения, максимум 40 слов) и метка влияния на бизнес.

Метка impact: "positive" | "negative" | "neutral".

Формат ответа — ТОЛЬКО JSON-массив. Первый символ «[», последний «]». Без markdown, без ```, без вступлений и дисклеймеров. Если ничего не подходит — верни [].

Формат элемента:
{{"title": "заголовок", "summary": "резюме", "source": "источник", "date": "YYYY-MM-DD", "url": "ссылка", "impact": "positive|negative|neutral"}}"""


def _call_giga_analyze(giga, system_prompt: str, news_text: str) -> str:
    payload = Chat(
        model="GigaChat-2",
        messages=[
            Messages(role="system", content=system_prompt),
            Messages(role="user", content="Список новостей:\n\n" + news_text),
        ],
        temperature=0.2,
        max_tokens=4000,
    )
    response = giga.chat(payload)
    return (response.choices[0].message.content or "").strip()


def gigachat_analyze_news(news_text: str, subject: str, indicators: List[str] = None) -> list:
    if not AUTH_TOKEN:
        return [{
            "title": "Нет токена GigaChat",
            "summary": "Проверьте файл .env",
            "source": "Система",
            "date": "",
            "url": "",
            "impact": "neutral"
        }]

    system_prompt = _build_analyze_system_prompt(subject, indicators)

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    try:
        with GigaChat(credentials=AUTH_TOKEN, verify_ssl_certs=False) as giga:
            attempts = [news_text, _strip_sensitive_lines(news_text)]
            for i, body in enumerate(attempts):
                if not body.strip():
                    continue

                raw = _call_giga_analyze(giga, system_prompt, body)

                parsed = _parse_news_json(raw)
                if parsed is not None:
                    note = " (без острых новостей)" if i > 0 else ""
                    print(f"GigaChat OK: проанализировано, выбрано "
                          f"{len(parsed)} новостей{note}")
                    return parsed

                if _looks_like_refusal(raw):
                    print(f"GigaChat отказался анализировать (попытка {i + 1}), "
                          f"повторяем без острых новостей")
                    continue

                print(f"Ошибка GigaChat: ответ не является JSON: {raw[:200]}")

            return None
    except Exception as e:
        print(f"Ошибка GigaChat: {e}")
        return None