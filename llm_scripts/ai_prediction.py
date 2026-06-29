from statsmodels.tsa.holtwinters import ExponentialSmoothing
from statsmodels.tsa.statespace.sarimax import SARIMAX
from prophet import Prophet
from text_generation import gigachat_anomalies

import pandas as pd


def data_transformation(data: dict[int, dict[int, float]]) -> pd.Series:
    values = {}
    for year, months in data.items():
        for month, metric in months.items():
            values[pd.Timestamp(year, month, 1)] = metric
    series = pd.Series(values).sort_index()
    series = series.asfreq("MS") # ms - month series
    return series


def exponential_smoothing(data: dict[int, dict[int, float]], n:int) -> pd.DataFrame:
    series = data_transformation(data)
    model = ExponentialSmoothing(
        series,
        trend="add", # "add" - линейный рост, "mul" - процентный рост, None - тренда нет
        seasonal="add", # "add" - сезонность фиксирована, "mul" - растёт вместе с метрикой, None - сезонности нет
        seasonal_periods=n
    )
    fit = model.fit()
    prediction = fit.forecast(n)
    return prediction


def sarimax_prediction(data: dict[int, dict[int, float]], n:int) -> pd.DataFrame:
    series = data_transformation(data)
    model = SARIMAX(
        series,
        order=(1,1,1), # (p, d, q)
        # p - сколько прошлых точек влияет на текущую
        # d - дифференцирование (0 - если тренда нет, ряд стабилен, 1 - убираем линейный рост, 2 - убирает квадратичный рост)
        # q - ошибка модели
        seasonal_order=(1,1,1,n) # (P, D, Q, s) то же самое но для сезонности
    )
    fit = model.fit()
    prediction = fit.forecast(n)
    return prediction


def prophet_prediction(data: dict[int, dict[int, float]], n: int) -> pd.DataFrame:
    series = data_transformation(data)
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


if __name__ == "__main__":
    input_data = {
    2020: {1: 100070, 2: 100070, 3: 100070, 4: 100070, 5: 100070, 6: 100070, 7: 100070, 8: 100070, 9: 100070, 10: 100070, 11: 100070, 12: 100070},
    2021: {1: 112768, 2: 112768, 3: 112768, 4: 112768, 5: 112768, 6: 112768, 7: 112768, 8: 112768, 9: 112768, 10: 112768, 11: 112768, 12: 112768},
    2022: {1: 125638, 2: 125638, 3: 125638, 4: 1256, 5: 125638, 6: 125638, 7: 125638, 8: 125638, 9: 125638, 10: 125638, 11: 125638, 12: 125638},
    2023: {1: 138882, 2: 138882, 3: 138882, 4: 138882, 5: 138882, 6: 138882, 7: 138882, 8: 138882, 9: 138882, 10: 138882, 11: 138882, 12: 138882},
    2024: {1: 162124, 2: 162124, 3: 162124, 4: 162124, 5: 16212444, 6: 162124, 7: 162124, 8: 162124, 9: 162124, 10: 162124, 11: 162124, 12: 162124},
    2025: {1: 180861, 2: 180861, 3: 180861, 4: 180861, 5: 180861, 6: 180861, 7: 180861, 8: 180861, 9: 180861, 10: 180861, 11: 180861, 12: 180861}
    }

    print (exponential_smoothing(input_data, 12))
    print (sarimax_prediction(input_data, 12))
    print (prophet_prediction(input_data, 12))


