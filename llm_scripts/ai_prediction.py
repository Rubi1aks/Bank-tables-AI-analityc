from statsmodels.tsa.holtwinters import ExponentialSmoothing
from statsmodels.tsa.statespace.sarimax import SARIMAX
from prophet import Prophet

import pandas as pd
import time
from statsmodels.tsa.seasonal import STL
from statsmodels.tsa.api import SimpleExpSmoothing
from sklearn.linear_model import Ridge
from sklearn.linear_model import LinearRegression

import logging
import warnings

logging.getLogger("cmdstanpy").disabled = True
logging.getLogger("prophet").disabled = True

warnings.filterwarnings("ignore")

def data_transformation(data: dict[str, dict[str, float]]) -> pd.Series:
    values = {}

    for year, months in data.items():
        year = int(year)

        for month, metric in months.items():
            month = int(month)
            values[pd.Timestamp(year=year, month=month, day=1)] = metric

    series = pd.Series(values).sort_index()
    series = series.asfreq("MS")  # MS = Month Start

    return series


def exponential_smoothing(series: pd.Series, n:int) -> pd.DataFrame:
    model = ExponentialSmoothing(
        series,
        trend="add", # "add" - линейный рост, "mul" - процентный рост, None - тренда нет
        seasonal="add", # "add" - сезонность фиксирована, "mul" - растёт вместе с метрикой, None - сезонности нет
        seasonal_periods=12
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
    fit = model.fit(disp=False)
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


def compare_models(data: dict[str, dict[str, float]], n: int) -> pd.DataFrame:
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
                "forecast": future_prediction,
                "error": None
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
      "административный_персонал": {
        "2022": {
          "1": 4975.0,
          "2": 4609.0,
          "3": 4718.0,
          "4": 4811.0,
          "5": 4971.0,
          "6": 4528.0,
          "7": 4954.0,
          "8": 4649.0,
          "9": 4645.0,
          "10": 4784.0,
          "11": 4963.0,
          "12": 4788.0
        },
        "2023": {
          "1": 5179.0,
          "2": 5355.0,
          "3": 5335.0,
          "4": 5288.0,
          "5": 5364.0,
          "6": 5429.0,
          "7": 5343.0,
          "8": 5440.0,
          "9": 5085.0,
          "10": 5191.0,
          "11": 5133.0,
          "12": 5478.0
        },
        "2024": {
          "1": 5676.0,
          "2": 6064.0,
          "3": 5757.0,
          "4": 5494.0,
          "5": 5718.0,
          "6": 5597.0,
          "7": 5761.0,
          "8": 5661.0,
          "9": 6024.0,
          "10": 5806.0,
          "11": 5835.0,
          "12": 5984.0
        }
      },
      "доля_людей_питающихся_в_столовой": {
        "2022": {
          "1": 66.61,
          "2": 73.3,
          "3": 71.75,
          "4": 66.17,
          "5": 59.65,
          "6": 27.63,
          "7": 12.93,
          "8": 17.1,
          "9": 75.01,
          "10": 74.36,
          "11": 70.3,
          "12": 47.94
        },
        "2023": {
          "1": 68.78,
          "2": 66.95,
          "3": 66.85,
          "4": 67.96,
          "5": 55.59,
          "6": 27.23,
          "7": 13.69,
          "8": 16.27,
          "9": 70.13,
          "10": 70.34,
          "11": 72.23,
          "12": 45.59
        },
        "2024": {
          "1": 65.79,
          "2": 72.99,
          "3": 66.1,
          "4": 69.37,
          "5": 55.35,
          "6": 25.68,
          "7": 14.01,
          "8": 16.86,
          "9": 71.0,
          "10": 75.83,
          "11": 66.85,
          "12": 46.11
        }
      },
      "доход_банка": {
        "2022": {
          "1": 0.0,
          "2": 367578.76,
          "3": 594212.1,
          "4": 686303.04,
          "5": 431679.96,
          "6": 242271.78,
          "7": 62250.68,
          "8": 181118.55,
          "9": 602933.87,
          "10": 510357.82,
          "11": 638664.88,
          "12": 472506.31
        },
        "2023": {
          "1": 754126.46,
          "2": 759349.43,
          "3": 834363.85,
          "4": 705688.68,
          "5": 633036.98,
          "6": 185085.27,
          "7": 98634.67,
          "8": 137189.05,
          "9": 640235.55,
          "10": 424456.89,
          "11": 832401.8,
          "12": 494171.12
        },
        "2024": {
          "1": 709224.47,
          "2": 758422.84,
          "3": 837991.21,
          "4": 777490.18,
          "5": 434804.24,
          "6": 300563.76,
          "7": 137801.62,
          "8": 204758.38,
          "9": 1154151.33,
          "10": 1099022.66,
          "11": 1011732.72,
          "12": 572041.11
        }
      },
      "количество_клиентов": {
        "2022": {
          "1": 57433.0,
          "2": 61060.0,
          "3": 60694.0,
          "4": 56623.0,
          "5": 51995.0,
          "6": 23137.0,
          "7": 10966.0,
          "8": 14071.0,
          "9": 62260.0,
          "10": 63098.0,
          "11": 59459.0,
          "12": 40818.0
        },
        "2023": {
          "1": 65616.0,
          "2": 63147.0,
          "3": 63988.0,
          "4": 64831.0,
          "5": 52590.0,
          "6": 25570.0,
          "7": 12576.0,
          "8": 15485.0,
          "9": 65408.0,
          "10": 65185.0,
          "11": 67622.0,
          "12": 42847.0
        },
        "2024": {
          "1": 65322.0,
          "2": 75554.0,
          "3": 67353.0,
          "4": 70529.0,
          "5": 57726.0,
          "6": 26602.0,
          "7": 14403.0,
          "8": 17189.0,
          "9": 74093.0,
          "10": 76002.0,
          "11": 69550.0,
          "12": 47310.0
        }
      },
      "общее_количество_людей_в_вузе": {
        "2022": {
          "1": 86223.0,
          "2": 83302.0,
          "3": 84591.0,
          "4": 85573.0,
          "5": 87167.0,
          "6": 83742.0,
          "7": 84811.0,
          "8": 82290.0,
          "9": 83003.0,
          "10": 84856.0,
          "11": 84579.0,
          "12": 85146.0
        },
        "2023": {
          "1": 95401.0,
          "2": 94321.0,
          "3": 95719.0,
          "4": 95397.0,
          "5": 94604.0,
          "6": 93906.0,
          "7": 91863.0,
          "8": 95180.0,
          "9": 93267.0,
          "10": 92672.0,
          "11": 93621.0,
          "12": 93985.0
        },
        "2024": {
          "1": 99290.0,
          "2": 103514.0,
          "3": 101897.0,
          "4": 101672.0,
          "5": 104293.0,
          "6": 103593.0,
          "7": 102812.0,
          "8": 101953.0,
          "9": 104357.0,
          "10": 100227.0,
          "11": 104040.0,
          "12": 102603.0
        }
      },
      "объем_транзакций": {
        "2022": {
          "1": 21959507.55,
          "2": 24182813.0,
          "3": 21686573.14,
          "4": 21787397.94,
          "5": 19100883.2,
          "6": 8842035.92,
          "7": 3866501.94,
          "8": 5280424.17,
          "9": 24410278.2,
          "10": 23627677.08,
          "11": 22567663.45,
          "12": 16293321.06
        },
        "2023": {
          "1": 27623679.84,
          "2": 28228603.41,
          "3": 25751970.6,
          "4": 26136617.65,
          "5": 23532973.2,
          "6": 9844961.4,
          "7": 5604242.88,
          "8": 6471181.5,
          "9": 27014158.08,
          "10": 28109727.55,
          "11": 28703510.34,
          "12": 19228448.19
        },
        "2024": {
          "1": 31106336.4,
          "2": 33264159.58,
          "3": 32230431.09,
          "4": 31995480.85,
          "5": 25427148.48,
          "6": 11786814.16,
          "7": 6043930.89,
          "8": 7445759.13,
          "9": 36408559.27,
          "10": 33920452.62,
          "11": 32848465.0,
          "12": 22258408.8
        }
      },
      "средний_тариф_банка": {
        "2022": {
          "1": 2.45,
          "2": 1.52,
          "3": 2.74,
          "4": 3.15,
          "5": 2.26,
          "6": 2.74,
          "7": 1.61,
          "8": 3.43,
          "9": 2.47,
          "10": 2.16,
          "11": 2.83,
          "12": 2.9
        },
        "2023": {
          "1": 2.73,
          "2": 2.69,
          "3": 3.24,
          "4": 2.7,
          "5": 2.69,
          "6": 1.88,
          "7": 1.76,
          "8": 2.12,
          "9": 2.37,
          "10": 1.51,
          "11": 2.9,
          "12": 2.57
        },
        "2024": {
          "1": 2.28,
          "2": 2.28,
          "3": 2.6,
          "4": 2.43,
          "5": 1.71,
          "6": 2.55,
          "7": 2.28,
          "8": 2.75,
          "9": 3.17,
          "10": 3.24,
          "11": 3.08,
          "12": 2.57
        }
      },
      "средняя_стоимость_обеда": {
        "2022": {
          "1": 382.35,
          "2": 396.05,
          "3": 357.31,
          "4": 384.78,
          "5": 367.36,
          "6": 382.16,
          "7": 352.59,
          "8": 375.27,
          "9": 392.07,
          "10": 374.46,
          "11": 379.55,
          "12": 399.17
        },
        "2023": {
          "1": 420.99,
          "2": 447.03,
          "3": 402.45,
          "4": 403.15,
          "5": 447.48,
          "6": 385.02,
          "7": 445.63,
          "8": 417.9,
          "9": 413.01,
          "10": 431.23,
          "11": 424.47,
          "12": 448.77
        },
        "2024": {
          "1": 476.2,
          "2": 440.27,
          "3": 478.53,
          "4": 453.65,
          "5": 440.48,
          "6": 443.08,
          "7": 419.63,
          "8": 433.17,
          "9": 491.39,
          "10": 446.31,
          "11": 472.3,
          "12": 470.48
        }
      },
      "студенты": {
        "2022": {
          "1": 81248.0,
          "2": 78693.0,
          "3": 79873.0,
          "4": 80762.0,
          "5": 82196.0,
          "6": 79214.0,
          "7": 79857.0,
          "8": 77641.0,
          "9": 78358.0,
          "10": 80072.0,
          "11": 79616.0,
          "12": 80358.0
        },
        "2023": {
          "1": 90222.0,
          "2": 88966.0,
          "3": 90384.0,
          "4": 90109.0,
          "5": 89240.0,
          "6": 88477.0,
          "7": 86520.0,
          "8": 89740.0,
          "9": 88182.0,
          "10": 87481.0,
          "11": 88488.0,
          "12": 88507.0
        },
        "2024": {
          "1": 93614.0,
          "2": 97450.0,
          "3": 96140.0,
          "4": 96178.0,
          "5": 98575.0,
          "6": 97996.0,
          "7": 97051.0,
          "8": 96292.0,
          "9": 98333.0,
          "10": 94421.0,
          "11": 98205.0,
          "12": 96619.0
        }
      }
    }

    all_results = []

    for metric_name, metric_data in input_data.items():
        print("=" * 80)
        print(f"Метрика: {metric_name}")
        print("=" * 80)

        result = compare_models(metric_data, n=6)

        print(
            result[
                ["model", "MAE", "RMSE", "MAPE", "time_sec", "error"]
            ].to_string(index=False)
        )

        print()

        # сохраняем результаты
        all_results.append(result)

    summary = (
        pd.concat(all_results, ignore_index=True)
        .groupby("model", as_index=False)
        .agg({
            "MAE": "mean",
            "RMSE": "mean",
            "MAPE": "mean",
            "time_sec": "mean"
        })
        .sort_values("RMSE")
    )

    print("=" * 80)
    print("СРЕДНИЕ ПОКАЗАТЕЛИ ПО ВСЕМ МЕТРИКАМ")
    print("=" * 80)
    print(summary.to_string(index=False))