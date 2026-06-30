from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, Dict, List
import pandas as pd
from ai_prediction import exponential_smoothing, sarimax_prediction, prophet_prediction
from text_generation import gigachat_anomalies, gigachat_text

app = FastAPI()

# --- Модели данных ---
class PredictRequest(BaseModel):
    subject: str
    horizon: int = 6
    method: str = "sarimax"  # "sarimax", "exponential", "prophet"

class PredictResponse(BaseModel):
    scenarioType: str
    description: str
    points: List[Dict]
    qualityScore: float

# --- Эндпоинты ---
@app.post("/predict")
def predict(data: PredictRequest):
    try:
        # 1. Получить данные из БД (пока заглушка)
        # TODO: Заменить на реальный вызов к Java-бэку или чтение из БД
        input_data = get_historical_data(data.subject)
        
        # 2. Выбрать метод
        if data.method == "exponential":
            prediction = exponential_smoothing(input_data, data.horizon)
        elif data.method == "prophet":
            prediction = prophet_prediction(input_data, data.horizon)
        else:
            prediction = sarimax_prediction(input_data, data.horizon)
        
        # 3. Форматировать ответ
        points = []
        for period, value in prediction.items():
            points.append({
                "period": period.strftime("%Y-%m") if hasattr(period, "strftime") else str(period),
                "value": float(value),
                "lowerBound": float(value * 0.85),
                "upperBound": float(value * 1.15)
            })
        
        return PredictResponse(
            scenarioType="BASELINE",
            description=f"Прогноз по методу {data.method} для {data.subject}",
            points=points,
            qualityScore=0.12
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/anomalies")
def get_anomalies(subject: str):
    # TODO: Реализовать анализ аномалий
    return {"anomalies": []}

@app.get("/news")
def get_news(subject: str):
    # TODO: Реализовать парсинг новостей
    return {"news": []}

# --- Вспомогательные функции ---
def get_historical_data(subject: str) -> Dict[int, Dict[int, float]]:
    """
    Заглушка. В реальности — запрос к Java-бэку или чтение из БД.
    """
    # Пример данных (замени на реальные)
    return {
        2020: {1: 100070, 2: 100070, 3: 100070, 4: 100070, 5: 100070, 6: 100070, 
               7: 100070, 8: 100070, 9: 100070, 10: 100070, 11: 100070, 12: 100070},
        2021: {1: 112768, 2: 112768, 3: 112768, 4: 112768, 5: 112768, 6: 112768, 
               7: 112768, 8: 112768, 9: 112768, 10: 112768, 11: 112768, 12: 112768},
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5000)