from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, Dict, List, Any
import pandas as pd
import requests
import os
import json
from ai_prediction import exponential_smoothing, sarimax_prediction, prophet_prediction
from text_generation import gigachat_anomalies, gigachat_text

app = FastAPI(title="University Analytics AI Service", version="1.0.0")


# ============================================================
# МОДЕЛИ ДАННЫХ
# ============================================================
class PredictRequest(BaseModel):
    subject: str
    horizon: int = 6
    method: str = "sarimax"
    data: Optional[Dict[str, Any]] = None  # исторические данные от Java
    multipliers: Optional[Dict[str, float]] = None  # множители драйверов


class PredictResponse(BaseModel):
    scenarioType: str
    description: str
    points: List[Dict]
    qualityScore: float


# ============================================================
# ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
# ============================================================
def get_data_from_java(subject: str) -> Dict[int, Dict[int, float]]:
    """
    Запрашивает исторические данные из Java-бэка
    """
    java_url = os.getenv("JAVA_BACKEND_URL", "http://host.docker.internal:8080")
    try:
        response = requests.get(
            f"{java_url}/api/facts?subject={subject}",
            timeout=10
        )
        if response.status_code != 200:
            return get_fallback_data(subject)

        facts = response.json()
        # Группируем по годам и месяцам
        result = {}
        for fact in facts:
            if fact.get("indicator") == "Доход банка":
                period = fact.get("period")
                year, month = map(int, period.split("-"))
                value = fact.get("value", 0.0)
                result.setdefault(year, {})[month] = value
        return result
    except Exception as e:
        print(f"Error fetching data from Java: {e}")
        return get_fallback_data(subject)


def get_fallback_data(subject: str) -> Dict[int, Dict[int, float]]:
    """
    Заглушка, если Java-бэк недоступен
    """
    return {
        2020: {m: 100070 for m in range(1, 13)},
        2021: {m: 112768 for m in range(1, 13)},
        2022: {m: 125638 for m in range(1, 13)},
        2023: {m: 138882 for m in range(1, 13)},
        2024: {m: 162124 for m in range(1, 13)},
        2025: {m: 180861 for m in range(1, 13)},
    }


def apply_multipliers(history: Dict[int, Dict[int, float]], multipliers: Dict[str, float]) -> Dict[
    int, Dict[int, float]]:
    """
    Применяет множители к драйверам (для кастомных сценариев)
    """
    # Здесь можно масштабировать историю на множители
    # Например, умножить доход на коэффициент
    if not multipliers:
        return history
    # Простейший вариант: умножаем весь доход на средний множитель
    factor = sum(multipliers.values()) / len(multipliers) if multipliers else 1.0
    return {
        year: {
            month: value * factor for month, value in months.items()
        } for year, months in history.items()
    }


# ============================================================
# ЭНДПОИНТЫ
# ============================================================
@app.post("/predict")
def predict(data: PredictRequest):
    try:
        # 1. Получаем исторические данные
        if data.data:
            history = data.data.get("history", {})
        else:
            history = get_data_from_java(data.subject)

        # Преобразуем в нужный формат, если пришло как list
        if isinstance(history, list):
            # парсим список фактов в словарь
            parsed = {}
            for item in history:
                period = item.get("period")
                if period:
                    year, month = map(int, period.split("-"))
                    parsed.setdefault(year, {})[month] = item.get("value", 0.0)
            history = parsed

        # 2. Применяем множители (для кастомных сценариев)
        if data.multipliers:
            history = apply_multipliers(history, data.multipliers)

        # 3. Выбираем метод прогнозирования
        method = data.method.lower()
        if method == "exponential":
            prediction = exponential_smoothing(history, data.horizon)
        elif method == "prophet":
            prediction = prophet_prediction(history, data.horizon)
        else:
            prediction = sarimax_prediction(history, data.horizon)

        # 4. Форматируем ответ
        points = []
        for period, value in prediction.items():
            period_str = period.strftime("%Y-%m") if hasattr(period, "strftime") else str(period)
            points.append({
                "period": period_str,
                "value": float(value),
                "lowerBound": float(value * 0.85),
                "upperBound": float(value * 1.15)
            })

        return PredictResponse(
            scenarioType="BASELINE",
            description=f"Прогноз по методу {method} для {data.subject}",
            points=points,
            qualityScore=0.12
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/anomalies")
def get_anomalies(subject: str):
    """
    Возвращает аномалии по региону
    TODO: Реальная реализация с обнаружением выбросов
    """
    try:
        history = get_data_from_java(subject)
        # Превращаем историю в DataFrame для анализа
        df = pd.DataFrame([
            {"date": pd.Timestamp(year, month, 1), "value": val}
            for year, months in history.items()
            for month, val in months.items()
        ]).sort_values("date")

        # Простой поиск аномалий: отклонение > 2 стандартных отклонений
        if len(df) > 5:
            mean = df["value"].mean()
            std = df["value"].std()
            anomalies = []
            for _, row in df.iterrows():
                if abs(row["value"] - mean) > 2 * std:
                    anomalies.append({
                        "id": f"an-{subject}-{row['date'].strftime('%Y-%m')}",
                        "indicator": "Доход банка",
                        "period": row["date"].strftime("%Y-%m"),
                        "subject": subject,
                        "deviationPct": round((row["value"] - mean) / mean * 100, 1),
                        "direction": "up" if row["value"] > mean else "down",
                        "text": f"{row['date'].strftime('%B %Y')}: доход в {subject} {'вырос' if row['value'] > mean else 'снизился'} на {abs(round((row['value'] - mean) / mean * 100, 1))}% от среднего"
                    })
            return {"anomalies": anomalies}
        return {"anomalies": []}
    except Exception as e:
        return {"anomalies": [], "error": str(e)}


@app.get("/news")
def get_news(subject: str):
    """
    Генерирует новостные подсказки через GigaChat
    """
    try:
        # Получаем данные для контекста
        history = get_data_from_java(subject)
        latest = {}
        for year, months in history.items():
            for month, value in months.items():
                latest[f"{year}-{month:02d}"] = value

        # Формируем промпт для GigaChat
        context = f"Данные по региону {subject} за последние месяцы: {list(latest.items())[-6:]}"
        news_text = gigachat_text(context)

        # Парсим ответ GigaChat в список новостей
        # (заглушка — возвращаем сгенерированный текст как одну новость)
        return {
            "news": [
                {
                    "id": f"news-{subject}-1",
                    "title": f"Анализ рынка для {subject}",
                    "source": "AI-аналитика",
                    "date": pd.Timestamp.now().strftime("%Y-%m"),
                    "summary": news_text[:500] if news_text else "Новостей нет",
                    "impact": "neutral"
                }
            ]
        }
    except Exception as e:
        return {"news": [], "error": str(e)}


@app.get("/summary")
def get_summary(subject: str):
    """
    Возвращает текстовое резюме по региону (для AI-аналитики)
    """
    try:
        history = get_data_from_java(subject)
        if not history:
            return {
                "marketNews": f"Нет данных по региону {subject}",
                "aiPlanSummary": "Нет данных для анализа"
            }

        # Берем последние 6 месяцев
        all_values = []
        for year in sorted(history.keys(), reverse=True):
            for month in sorted(history[year].keys(), reverse=True):
                all_values.append(history[year][month])
                if len(all_values) >= 6:
                    break
            if len(all_values) >= 6:
                break

        if len(all_values) < 2:
            return {
                "marketNews": f"Недостаточно данных для анализа по {subject}",
                "aiPlanSummary": "Нет данных для прогноза"
            }

        # Простой анализ тренда
        trend = "стагнация"
        if all_values[-1] > all_values[0] * 1.05:
            trend = "рост"
        elif all_values[-1] < all_values[0] * 0.95:
            trend = "спад"

        # Генерируем резюме через GigaChat (если доступен)
        try:
            context = f"Регион: {subject}. Тренд: {trend}. Доход за последние 6 месяцев: {all_values}"
            ai_text = gigachat_text(context)
        except:
            ai_text = f"Анализ показывает {trend} доходов в регионе {subject}."

        return {
            "marketNews": f"Рынок в регионе {subject} демонстрирует {trend}.",
            "aiPlanSummary": ai_text
        }
    except Exception as e:
        return {
            "marketNews": "Ошибка получения данных",
            "aiPlanSummary": str(e)
        }


@app.get("/health")
def health():
    return {"status": "healthy"}


# ============================================================
# ЗАПУСК
# ============================================================
if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=5000)