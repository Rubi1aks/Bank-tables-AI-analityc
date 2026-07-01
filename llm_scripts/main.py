from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import pandas as pd
import uvicorn

from ai_prediction import compare_models
from text_generation import gigachat_anomalies, gigachat_text

app = FastAPI(title="FinTech Analytics AI Service")


class MultiPredictionRequest(BaseModel):
    data: dict[str, dict[str, dict[str, float]]]
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


@app.post("/api/predict/all_models")
def predict_all_models(payload: MultiPredictionRequest):

    try:
        result = {}

        for metric_name, metric_data in payload.data.items():

            df = compare_models(metric_data, payload.n_periods)

            best_model = df.iloc[0]["model"]

            models_output = []

            for _, row in df.iterrows():

                forecast = row["forecast"]

                # Series -> JSON
                forecast_json = {
                    str(k.date()): float(v)
                    for k, v in forecast.items()
                }

                models_output.append({
                    "name": row["model"],
                    "metrics": {
                        "MAE": float(row["MAE"]) if row["MAE"] == row["MAE"] else None,
                        "RMSE": float(row["RMSE"]) if row["RMSE"] == row["RMSE"] else None,
                        "MAPE": float(row["MAPE"]) if row["MAPE"] == row["MAPE"] else None,
                        "time_sec": float(row["time_sec"])
                    },
                    "forecast": forecast_json,
                    "best": row["model"] == best_model
                })

            result[metric_name] = {
                "models": models_output,
                "best_model": best_model
            }

        return result

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
