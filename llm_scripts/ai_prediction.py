from statsmodels.tsa.holtwinters import ExponentialSmoothing
from statsmodels.tsa.statespace.sarimax import SARIMAX
from prophet import Prophet

import pandas as pd
import time
from statsmodels.tsa.seasonal import STL
from statsmodels.tsa.api import SimpleExpSmoothing
from sklearn.linear_model import Ridge
from sklearn.linear_model import LinearRegression


def data_transformation(data: dict[int, dict[int, float]]) -> pd.Series:
    values = {}
    for year, months in data.items():
        for month, metric in months.items():
            values[pd.Timestamp(year, month, 1)] = metric
    series = pd.Series(values).sort_index()
    series = series.asfreq("MS") # ms - month start
    return series


def exponential_smoothing(series: pd.Series, n:int) -> pd.DataFrame:
    model = ExponentialSmoothing(
        series,
        trend="add", # "add" - линейный рост, "mul" - процентный рост, None - тренда нет
        seasonal="add", # "add" - сезонность фиксирована, "mul" - растёт вместе с метрикой, None - сезонности нет
        seasonal_periods=n
    )
    fit = model.fit()
    prediction = fit.forecast(n)
    return prediction


def sarimax_prediction(series: pd.Series, n:int) -> pd.DataFrame:
    model = SARIMAX(
        series,
        order=(1,1,1), # (p, d, q)
        # p - сколько прошлых точек влияет на текущую
        # d - дифференцирование (0 - если тренда нет, ряд стабилен, 1 - убираем линейный рост, 2 - убирает квадратичный рост)
        # q - ошибка модели
        seasonal_order=(1,1,1,12) # (P, D, Q, s) то же самое но для сезонности
    )
    fit = model.fit()
    prediction = fit.forecast(n)
    return prediction


def prophet_prediction(series: pd.Series, n: int) -> pd.DataFrame:
    df = pd.DataFrame({
        "ds": series.index,
        "y": series.values
    })
    model = Prophet(
        yearly_seasonality=True,
        weekly_seasonality=False,
        daily_seasonality=False
    )
    model.fit(df)
    future = model.make_future_dataframe(
        periods=n,
        freq="MS"
    )
    prediction = model.predict(future)
    return prediction


def stl_decomposition_prediction(series: pd.Series, n: int) -> pd.Series:
    res = STL(series, period=12, robust=True).fit() # robust=True защищает от аномалий

    trend = res.trend
    x = np.arange(len(trend)).reshape(-1, 1)
    y = trend.values

    trend_model = LinearRegression().fit(x, y)

    future_x = np.arange(len(trend), len(trend) + n).reshape(-1, 1)
    future_trend = trend_model.predict(future_x)

    last_year_seasonal = res.seasonal.iloc[-12:].values
    future_seasonal = np.array([last_year_seasonal[i % 12] for i in range(n)])

    prediction_values = future_trend + future_seasonal

    future_index = pd.date_range(start=series.index[-1] + pd.offsets.MonthBegin(1), periods=n, freq="MS")
    return pd.Series(prediction_values, index=future_index)


def ridge_regression_prediction(series: pd.Series, n: int) -> pd.Series:
    df = pd.DataFrame({'y': series})
    for i in range(1, 13):
        df[f'lag_{i}'] = df['y'].shift(i)

    df = df.dropna()

    X = df.drop(columns=['y']).values
    y = df['y'].values
    model = Ridge(alpha=1.0)
    model.fit(X, y)

    current_inputs = list(series.iloc[-12:].values[::-1])
    predictions = []

    for _ in range(n):
        pred = model.predict([current_inputs])[0]
        predictions.append(pred)
        current_inputs = [pred] + current_inputs[:-1]

    future_index = pd.date_range(start=series.index[-1] + pd.offsets.MonthBegin(1), periods=n, freq="MS")
    return pd.Series(predictions, index=future_index)


def croston_prediction(series: pd.Series, n: int) -> pd.Series:
    zero_fraction = (series == 0).sum() / len(series)

    if zero_fraction > 0.15:
        y = series.values

        nonzero_indices = np.where(y != 0)[0]

        if len(nonzero_indices) < 2:
            return SimpleExpSmoothing(series).fit().forecast(n)

        z = y[nonzero_indices]
        p = np.diff(np.insert(nonzero_indices, 0, -1))
        alpha = 0.2

        z_fit = np.zeros(len(z))
        p_fit = np.zeros(len(p))
        z_fit[0] = z[0]
        p_fit[0] = p[0]

        for t in range(1, len(z)):
            z_fit[t] = alpha * z[t] + (1 - alpha) * z_fit[t - 1]
            p_fit[t] = alpha * p[t] + (1 - alpha) * p_fit[t - 1]

        croston_val = z_fit[-1] / p_fit[-1] if p_fit[-1] != 0 else 0

        future_index = pd.date_range(start=series.index[-1] + pd.offsets.MonthBegin(1), periods=n, freq="MS")
        return pd.Series([croston_val] * n, index=future_index)

    else:
        model = SimpleExpSmoothing(series)
        fit = model.fit()
        return fit.forecast(n)

from sklearn.metrics import (
    mean_absolute_error,
    mean_squared_error,
    mean_absolute_percentage_error,
)
import pandas as pd
import numpy as np


def compare_models(data: dict[int, dict[int, float]], n: int) -> pd.DataFrame:
    series = data_transformation(data)

    split_idx = int(len(series) * 0.8)
    split_idx = min(max(split_idx, 1), len(series) - 1)

    train = series.iloc[:split_idx]
    test = series.iloc[split_idx:]

    test_size = len(test)

    models = {
        "Exponential Smoothing": exponential_smoothing,
        "SARIMAX": sarimax_prediction,
        "Prophet": prophet_prediction,
        "STL": stl_decomposition_prediction,
        "Ridge Regression": ridge_regression_prediction,
        "Croston": croston_prediction,
    }

    results = []

    for name, model in models.items():
        start_time = time.perf_counter()
        try:
            prediction = model(train, test_size)

            if isinstance(prediction, pd.DataFrame):
                if "yhat" in prediction.columns:
                    prediction = prediction[["ds", "yhat"]].set_index("ds")["yhat"]
                    prediction = prediction.iloc[-test_size:]
                else:
                    prediction = prediction.iloc[:, 0]

            prediction = prediction.reindex(test.index)

            mae = mean_absolute_error(test, prediction)
            rmse = np.sqrt(mean_squared_error(test, prediction))
            mape = mean_absolute_percentage_error(test, prediction)

            future_prediction = model(series, n)

            if isinstance(future_prediction, pd.DataFrame):
                if "yhat" in future_prediction.columns:
                    future_prediction = (
                        future_prediction[["ds", "yhat"]]
                        .set_index("ds")["yhat"]
                        .iloc[-n:]
                    )
                else:
                    future_prediction = future_prediction.iloc[:, 0]

            elapsed = time.perf_counter() - start_time
            results.append({
                "model": name,
                "MAE": mae,
                "RMSE": rmse,
                "MAPE": mape,
                "time_sec": round(elapsed, 3),
                "forecast": future_prediction
            })

        except Exception as e:
            elapsed = time.perf_counter() - start_time
            results.append({
                "model": name,
                "MAE": np.nan,
                "RMSE": np.nan,
                "MAPE": np.nan,
                "time_sec": round(elapsed, 3),
                "forecast": None,
                "error": str(e)
            })

    return (
        pd.DataFrame(results)
        .sort_values("RMSE")
        .reset_index(drop=True)
    )

if __name__ == "__main__":
    input_data = {
        2020: {1: 100070, 2: 100070, 3: 100070, 4: 100070, 5: 100070, 6: 100070, 7: 100070, 8: 100070, 9: 100070,
               10: 100070, 11: 100070, 12: 100070},
        2021: {1: 112768, 2: 112768, 3: 112768, 4: 112768, 5: 112768, 6: 112768, 7: 112768, 8: 112768, 9: 112768,
               10: 112768, 11: 112768, 12: 112768},
        2022: {1: 125638, 2: 125638, 3: 125638, 4: 1256, 5: 125638, 6: 125638, 7: 125638, 8: 125638, 9: 125638,
               10: 125638, 11: 125638, 12: 125638},
        2023: {1: 138882, 2: 138882, 3: 138882, 4: 138882, 5: 138882, 6: 138882, 7: 138882, 8: 138882, 9: 138882,
               10: 138882, 11: 138882, 12: 138882},
        2024: {1: 162124, 2: 162124, 3: 162124, 4: 162124, 5: 16212444, 6: 162124, 7: 162124, 8: 162124, 9: 162124,
               10: 162124, 11: 162124, 12: 162124},
        2025: {1: 180861, 2: 180861, 3: 180861, 4: 180861, 5: 180861, 6: 180861, 7: 180861, 8: 180861, 9: 180861,
               10: 180861, 11: 180861, 12: 180861}
    }

    result = compare_models(input_data, n=6)

    print(result[["model", "MAE", "RMSE", "MAPE"]])

    best_forecast = result.iloc[0]["forecast"]

    print(best_forecast)