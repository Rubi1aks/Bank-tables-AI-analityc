from gigachat import GigaChat
from gigachat.models import Chat, Messages
from dotenv import load_dotenv
import os
import pandas as pd

load_dotenv()

AUTH_TOKEN = os.getenv("AUTH_TOKEN")


def gigachat_anomalies(anomalies: pd.DataFrame) -> str:
    promt = (
        "Ты анализируешь таблицу с аномалиями временного ряда.\n"
        "Твоя задача - описать каждую аномалию простым деловым языком.\n\n"
        "Правила:\n"
        "1. Используй только данные из таблицы.\n"
        "2. Не добавляй никаких предположений или анализа вне данных.\n"
        "3. Для каждой строки, где anomaly = True, сформируй одно предложение.\n"
        "4. Игнорируй строки где anomaly = False.\n"
        "5. Формат предложения строго такой:\n"
        "   'Внимание, в {месяц} {год} года обнаружен {тип аномалии} метрики на {pct_change:.2f}%.'\n"
        "6. Тип аномалии переводи так:\n"
        "   spike - резкий скачок\n"
        "   drop - резкое падение\n"
        "   outlier - аномальное изменение\n"
        "7. Месяц и год бери из date (формат YYYY-MM-DD).\n"
        "8. Если pct_change отрицательный, говори 'снижение', если положительный - 'рост'.\n"
        "9. Ответ должен быть списком предложений без заголовков и без пояснений.\n\n"
        "Вот данные:\n"
    )
    with GigaChat(credentials=AUTH_TOKEN, verify_ssl_certs=False) as giga:
        try:
            payload = Chat(
                model="GigaChat-Pro",
                messages=[Messages(role="user", content=promt + str(anomalies))],
                temperature=0.6,
                max_tokens=1000
            )
            response = giga.chat(payload)
            print(response.choices[0].message.content.strip())
            return response.choices[0].message.content.strip()

        except Exception as e:
            return f"Error: {e}"


def gigachat_text(data: str) -> str:
    promt = "Ты - ведущий FinTech-аналитик. Твоя задача - провести экспресс-анализ " \
           "предоставленных данных и прогноза для указанного региона. " \
           "Требования к ответу: Назови текущий тренд (рост/падение/стагнация) одной фразой; " \
           "Перечисли ключевые аномалии (просадки/пики) и дай им краткое логическое обоснование " \
           "(например, сезонность, каникулы); Укажи главные драйверы изменений " \
           "(например, изменение среднего чека, приток клиентов). Критическое требование к формату: " \
           "Пиши максимально кратко, тезисно и строго по делу. Исключи вводные слова, приветствия, " \
           "вежливые фразы и общие выводы. Только сухие факты и цифры в формате буллитов. " \
           "Максимальный объем ответа 3–4 предложения. Не используй форматирование, списки," \
            "НЕЛЬЗЯ маркирование с помощью тире как списки, можно использовать только текст. " \
            "Вот данные: "
    with GigaChat(credentials=AUTH_TOKEN, verify_ssl_certs=False) as giga:
        try:
            payload = Chat(
                model = "GigaChat-Pro",
                messages=[Messages(role="user", content=promt+data)],
                temperature=0.4,
                max_tokens=1000
            )
            response = giga.chat(payload)
            print(response.choices[0].message.content.strip())
            return response.choices[0].message.content.strip()

        except Exception as e:
            return f"Error: {e}"


if __name__ == "__main__":
    wide_table = {'columns': ['report_period', 'report_year', 'report_month',
                  'report_quarter', 'federal_district', 'subject_rf', 'city',
                  'fund_payroll_rub', 'avg_salary_rub', 'fishing_revenue_rub',
                  'arctic_subsidies_rub', 'payroll_growth_pct'],
                'rows': [('Апрель 2026', 2026, 4, 2, 'Северо-Западный федеральный округ',
                'Мурманская область', 'Мурманск', 8900000000, 48500, 850000000,
                300000000, 3.1), ('Май 2026', 2026, 5, 2, 'Северо-Западный федеральный округ',
              'Мурманская область', 'Мурманск', 9150000000, 50200, 1050000000, 380000000, 2.8),
               ('Июнь 2026', 2026, 6, 2, 'Северо-Западный федеральный округ',
                'Мурманская область', 'Мурманск', 9823456000, 53170, 1245600000,
                450000000, 6.2), ('Июль 2026', 2026, 7, 3, 'Северо-Западный федеральный округ',
              'Мурманская область', 'Мурманск', 9300000000, 49800, 780000000, 200000000, -5.3),
               ('Август 2026', 2026, 8, 3, 'Северо-Западный федеральный округ',
                'Мурманская область', 'Мурманск', 9450000000, 51000, 980000000, 280000000, 1.6)]}

    gigachat_text(str(wide_table))

