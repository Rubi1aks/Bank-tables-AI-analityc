from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import pandas as pd
import uvicorn

from ai_prediction import exponential_smoothing, sarimax_prediction, prophet_prediction
from text_generation import gigachat_anomalies, gigachat_text

app = FastAPI(title="FinTech Analytics AI Service")


class PredictionRequest(BaseModel):
    data: dict[int, dict[int, float]] # { "2024": { "1": 123.4, "2": 567.8 }, ... }
    n_periods: int = 12


class AnomalyRow(BaseModel):
    date: str # YYYY-MM-DD
    value: float
    anomaly: bool
    type: str # spike, drop, outlier
    pct_change: float


class AnomalyTableRequest(BaseModel):
    table: list[AnomalyRow]


#??????????????????
class RegionAnalysisRequest(BaseModel):
    data: dict


@app.post("/api/predict/exponential")
def predict_exponential(payload: PredictionRequest):
    try:
        prediction = exponential_smoothing(payload.data, payload.n_periods)
        # Конвертируем pandas.Series/DataFrame в JSON-совместимый словарь (timestamp -> value)
        return {"prediction": {str(k.date()): v for k, v in prediction.items()}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/predict/sarimax")
def predict_sarimax(payload: PredictionRequest):
    try:
        prediction = sarimax_prediction(payload.data, payload.n_periods)
        return {"prediction": {str(k.date()): v for k, v in prediction.items()}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/predict/prophet")
def predict_prophet(payload: PredictionRequest):
    try:
        prediction = prophet_prediction(payload.data, payload.n_periods)
        # Prophet возвращает DataFrame, забираем только прогнозные строки 'ds' и 'yhat'
        forecast_df = prediction[['ds', 'yhat']].tail(payload.n_periods)
        result = {str(row['ds'].date()): row['yhat'] for _, row in forecast_df.iterrows()}
        return {"prediction": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/analysis/anomalies")
def analyze_anomalies(payload: AnomalyTableRequest):
    try:
        data_dicts = [row.model_dump() for row in payload.table]
        df = pd.DataFrame(data_dicts)
        result_text = gigachat_anomalies(df)
        return {"analysis": result_text}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/analysis/region")
def analyze_region(payload: RegionAnalysisRequest):
    try:
        result_text = gigachat_text(str(payload.data))
        return {"analysis": result_text}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
