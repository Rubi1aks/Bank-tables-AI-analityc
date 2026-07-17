import feedparser
import requests
from datetime import datetime, timedelta, timezone
from typing import List, Dict, Optional, Set
import re
from dateutil import parser
import asyncio
from concurrent.futures import ThreadPoolExecutor

_executor = ThreadPoolExecutor(max_workers=4)
_FEED_TIMEOUT = 8
_FEED_HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; NewsBot/1.0)"}


def _fetch_feed(url: str):
    resp = requests.get(url, timeout=_FEED_TIMEOUT, headers=_FEED_HEADERS)
    resp.raise_for_status()
    return feedparser.parse(resp.content)

_STOPWORDS = {
    "количество", "доля", "средний", "среднее", "средняя", "общий", "общее",
    "общая", "значение", "показатель", "всего", "прочие", "прочее", "иные",
    "процент", "единиц", "штук", "мера", "измерения", "чистая", "чистый",
    "уровень", "объем", "объём", "число", "индекс", "коэффициент", "темп",
    "доход", "доходы", "доходность", "выручка", "прибыль", "рентабельность",
    "реализация", "реализации", "продукция", "продукции", "продуктивных",
    "продуктивные", "расход", "расходы", "стоимость", "цена", "сумма",
    "голову", "голова", "голов",
    "продаж", "продажи", "продажа", "продаж", "производство", "производства",
    "производственный", "новый", "новая", "новое", "новые", "новых",
    "зарегистрированный", "зарегистрированных", "зарегистрирован",
    "отечественный", "отечественная", "отечественное", "отечественные",
    "отечественных", "парк", "марка", "марки", "марок", "объем",
}

def _norm(s: str) -> str:
    return s.lower().replace("ё", "е")

_DOMAIN_CLUSTERS = [
    {
        "triggers": ["скот", "коров", "поголов", "надой", "молок", "птиц",
                     "свин", "овц", "хозяйств", "ферм", "животновод", "корм",
                     "урожай", "зерн", "агро", "теленок", "бык"],
        "expand": ["скот", "коров", "молок", "надой", "птиц", "свин", "овц",
                   "ферм", "животновод", "сельск", "агропром", "аграр",
                   "урожай", "корма", "падеж", "ветеринар", "мясо", "зерн",
                   "посев", "фермер", "птицефабрик", "засух", "эпизоот",
                   "грипп", "вспышк", "поголов", "удой", "убой", "сельхоз",
                   "продовольств", "минсельхоз"],
    },
    {
        "triggers": ["студент", "вуз", "университет", "обучен", "столов",
                     "питани", "общепит", "банан", "блюд", "кухн", "меню"],
        "expand": ["студент", "вуз", "университет", "образован", "общепит",
                   "столов", "питани", "ресторан", "кафе", "продукт",
                   "поставщик", "продовольств", "фрукт", "овощ"],
    },
    {
        "triggers": ["банк", "транзакц", "arpu", "эквайринг", "депозит",
                     "кредит", "вклад"],
        "expand": ["банк", "финанс", "кредит", "вклад", "депозит", "ставк",
                   "эквайринг", "платеж", "карт", "инфляц"],
    },
    {
        "triggers": ["автомобил", "автопром", "автоваз", "дилер", "иномар",
                     "легков"],
        "expand": ["автомобил", "автомобильн", "автопром", "автоваз",
                   "автосалон", "автопарк", "автодилер", "автозавод",
                   "автокредит", "авторынок", "автокомпонент", "машин",
                   "иномарк", "легков", "лада", "камаз", "дилер", "птс",
                   "утильсбор"],
    },
]


def build_topic_prefixes(indicators: Optional[List[str]]) -> Set[str]:
    if not indicators:
        return set()

    words: List[str] = []
    for ind in indicators:
        for w in re.split(r"[\s,/()«»\"'.\-]+", _norm(ind)):
            wl = w.strip()
            if len(wl) > 4 and wl not in _STOPWORDS:
                words.append(wl)

    prefixes = {w[:6] for w in words}

    blob = _norm(" ".join(indicators))
    for cluster in _DOMAIN_CLUSTERS:
        if any(t in blob for t in cluster["triggers"]):
            prefixes |= {_norm(e) for e in cluster["expand"]}

    return {p for p in prefixes if len(p) >= 4}


def _text_matches_topic(text: str, prefixes: Set[str]) -> bool:
    if not prefixes:
        return True
    for w in re.findall(r"[а-яёa-z]+", _norm(text)):
        for p in prefixes:
            if w.startswith(p):
                return True
    return False

_REGION_STOPWORDS = {
    "федеральный", "федеральная", "федеральное", "федерального", "федеральной",
    "округ", "округа", "округе", "область", "области", "областная",
    "край", "края", "крае", "республика", "республики", "республике",
    "автономный", "автономная", "автономного", "автономной", "автономная",
    "город", "города", "region", "district",
}

_REGION_ALIASES = {
    "московск": ["москв", "подмосков", "мособл"],
    "москва": ["москв", "подмосков", "мособл", "московск"],
    "петербург": ["петербург", "питер"],
    "ленинградск": ["петербург", "питер", "ленинград"],
}


def _region_stems(subject: str) -> List[str]:
    norm = _norm(subject)
    stems = []
    for w in re.split(r"[\s,/()«»\"'.\-]+", norm):
        wl = w.strip()
        if len(wl) > 3 and wl not in _REGION_STOPWORDS:
            stems.append(wl[:6])
    for key, aliases in _REGION_ALIASES.items():
        if key in norm:
            stems.extend(aliases)
    return stems


def _region_matches(text: str, subject: str) -> bool:
    if not subject:
        return True
    norm_text = _norm(text)
    if _norm(subject) in norm_text:
        return True
    stems = _region_stems(subject)
    if not stems:
        return True
    for w in re.findall(r"[а-яёa-z]+", norm_text):
        for st in stems:
            if w.startswith(st):
                return True
    return False


class NewsParser:
    def __init__(self):
        self.sources = [
            {"name": "РБК", "url": "https://rssexport.rbc.ru/rbcnews/news/30/full.rss"},
            {"name": "Ведомости", "url": "https://www.vedomosti.ru/rss/news"},
            {"name": "Коммерсантъ", "url": "https://www.kommersant.ru/RSS/news.xml"},
            {"name": "Интерфакс", "url": "https://www.interfax.ru/rss.asp"},
            {"name": "ТАСС", "url": "https://tass.ru/rss/v2.xml"},
        ]

    async def fetch_news(self, subject: str = "", period_days: int = 90,
                         indicators: List[str] = None, status_callback=None) -> List[Dict]:
        all_news = []
        since_date = datetime.now() - timedelta(days=period_days)

        topic_prefixes = build_topic_prefixes(indicators)

        tasks = []
        for source in self.sources:
            tasks.append(self._parse_source(source, subject, since_date, topic_prefixes))

        results = await asyncio.gather(*tasks, return_exceptions=True)

        for i, source_news in enumerate(results):
            if isinstance(source_news, Exception):
                print(f"Ошибка парсинга {self.sources[i]['name']}: {source_news}")
                continue

            all_news.extend(source_news)
            if status_callback and source_news:
                try:
                    import json
                    await status_callback(json.dumps({
                        "phase": "PARSED",
                        "source": source_news[0].get("source", "unknown") if source_news else "unknown",
                        "count": len(source_news),
                        "message": f"{source_news[0].get('source', 'unknown') if source_news else 'unknown'}: найдено {len(source_news)} новостей"
                    }))
                except Exception:
                    pass

        all_news.sort(key=lambda x: x["date"], reverse=True)
        return all_news

    async def _parse_source(self, source, subject, since_date, topic_prefixes):
        news = []
        try:
            loop = asyncio.get_event_loop()
            feed = await loop.run_in_executor(_executor, _fetch_feed, source["url"])

            for entry in feed.entries:
                pub_date = self._parse_date(entry.get("published", ""))
                if not pub_date or pub_date < since_date:
                    continue

                title = entry.get("title", "")
                summary = entry.get("summary", "")
                text = (title + " " + summary).lower()

                if not _region_matches(text, subject):
                    continue

                if not _text_matches_topic(text, topic_prefixes):
                    continue

                news.append({
                    "title": title,
                    "summary": self._clean_text(summary),
                    "source": source["name"],
                    "date": pub_date.strftime("%Y-%m-%d"),
                    "url": entry.get("link", ""),
                })
        except Exception as e:
            print(f"Ошибка парсинга {source['name']}: {e}")
        return news

    def _parse_date(self, date_str: str) -> Optional[datetime]:
        if not date_str:
            return None
        try:
            dt = parser.parse(date_str)
            if dt.tzinfo is not None:
                dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
            return dt
        except:
            return None

    def _clean_text(self, text: str) -> str:
        if not text:
            return ""
        text = re.sub(r'<[^>]+>', '', text)
        text = re.sub(r'\s+', ' ', text)
        return text.strip()


async def get_news_summary_raw(subject: str = "", period_days: int = 90,
                               indicators: List[str] = None, status_callback=None) -> List[Dict]:
    news_parser = NewsParser()
    return await news_parser.fetch_news(subject, period_days, indicators, status_callback)