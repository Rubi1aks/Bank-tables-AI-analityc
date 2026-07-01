from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from pydantic import BaseModel
from typing import Dict, List, Any, Optional
import pandas as pd
from ai_prediction import exponential_smoothing, sarimax_prediction, prophet_prediction
from text_generation import gigachat_text, gigachat_analyze_news
from news_parser import get_news_summary_raw
import numpy as np
import json
import asyncio
import requests

app = FastAPI(title="University Analytics AI Service")


class PredictRequest(BaseModel):
    subject: str
    indicator: str
    horizon: int = 6
    method: str = "sarimax"
    history: Dict[int, Dict[int, float]]


class PredictResponse(BaseModel):
    scenarioType: str
    description: str
    points: List[Dict[str, Any]]
    qualityScore: float


@app.post("/predict", response_model=PredictResponse)
def predict(data: PredictRequest):
    try:
        history = data.history
        method = data.method.lower()
        n = data.horizon

        if method == "exponential":
            prediction = exponential_smoothing(history, n)
        elif method == "prophet":
            prediction = prophet_prediction(history, n)
        else:
            prediction = sarimax_prediction(history, n)

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
            description=f"Прогноз по методу {method} для {data.indicator} в {data.subject}",
            points=points,
            qualityScore=0.12
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================
# WebSocket для новостей (с динамическими индикаторами!)
# ============================================================
# WebSocket для новостей
@app.websocket("/ws/news")
async def websocket_news(websocket: WebSocket):
    await websocket.accept()
    try:
        data = await websocket.receive_text()
        params = json.loads(data)
        subject = params.get('subject', '')
        period = params.get('period', 90)

        await websocket.send_text(json.dumps({"phase": "START", "message": "Начинаем сбор новостей..."}))

        # Получаем показатели из Java
        indicators = get_indicators_from_java()
        await websocket.send_text(json.dumps({"phase": "INDICATORS",
                                              "message": f"Получено {len(indicators)} показателей"}))

        # ✅ Асинхронно собираем новости (параллельно!)
        all_news = await get_news_summary_raw(
            subject, period, indicators,
            status_callback=websocket.send_text  # ✅ Теперь можно передавать awaitable!
        )

        if not all_news:
            await websocket.send_text(json.dumps({"phase": "DONE", "message": "Новостей не найдено", "data": []}))
            await websocket.close()
            return

        await websocket.send_text(json.dumps({"phase": "FOUND", "message": f"Найдено {len(all_news)} новостей"}))

        # Анализ через GigaChat
        await websocket.send_text(
            json.dumps({"phase": "ANALYZING", "message": "Анализируем важность новостей через AI..."}))

        top_news = all_news[:20]
        news_text = "Вот список новостей:\n\n"
        for i, news in enumerate(top_news, 1):
            news_text += f"{i}. {news['title']} ({news['source']}, {news['date']})\n"
            news_text += f"   {news['summary'][:200]}...\n\n"

        analyzed = gigachat_analyze_news(news_text, subject, indicators)

        if analyzed:
            await websocket.send_text(json.dumps({"phase": "DONE",
                                                  "message": f"Выбрано {len(analyzed)} самых важных новостей",
                                                  "data": analyzed}))
        else:
            await websocket.send_text(json.dumps({"phase": "DONE",
                                                  "message": f"GigaChat недоступен, возвращаем {len(top_news[:5])} свежих новостей",
                                                  "data": top_news[:5]}))

    except WebSocketDisconnect:
        print("WebSocket disconnected")
    except Exception as e:
        await websocket.send_text(json.dumps({"phase": "ERROR", "message": str(e)}))


# ============================================================
# Вспомогательная функция: получает показатели из Java-бэка
# ============================================================
def get_indicators_from_java():
    """Запрашивает список показателей из Java-бэка"""
    try:
        response = requests.get("http://localhost:8080/api/indicators", timeout=5)
        if response.status_code == 200:
            return response.json()
    except Exception as e:
        print(f"Ошибка получения показателей из Java: {e}")
    return []  # Если Java недоступен — возвращаем пустой список


# ============================================================
# Остальные эндпоинты
# ============================================================
@app.post("/generate-text")
def generate_text(data: Dict[str, Any]):
    try:
        context = f"Данные по региону {data.get('subject')}. Тренд: {data.get('trend')}. Прогноз: {data.get('forecast')}"
        text = gigachat_text(context)
        return {"summary": text}
    except Exception as e:
        return {"summary": f"Ошибка: {str(e)}"}


@app.post("/generate-news")
def generate_news(data: Dict[str, Any]):
    try:
        subject = data.get('subject', '')
        period = data.get('period', 90)
        indicators = get_indicators_from_java()
        news = get_news_summary_raw(subject, period, indicators)
        return {"news": news[:5] if news else []}
    except Exception as e:
        return {"news": [
            {"title": "Ошибка", "summary": str(e), "source": "Система", "date": "", "url": "", "impact": "neutral"}]}


@app.get("/health")
def health():
    return {"status": "healthy"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="127.0.0.1", port=5000, reload=True)