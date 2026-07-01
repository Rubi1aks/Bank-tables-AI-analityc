import pandas as pd
import json
import sys
from pathlib import Path

# Сопоставление русских названий месяцев с номерами
MONTH_MAP = {
    'январь': 1, 'февраль': 2, 'март': 3, 'апрель': 4,
    'май': 5, 'июнь': 6, 'июль': 7, 'август': 8,
    'сентябрь': 9, 'октябрь': 10, 'ноябрь': 11, 'декабрь': 12
}

# Столбцы, которые НЕ являются метриками (служебные)
NON_METRIC_COLUMNS = {'report_period', 'federal_district', 'region'}

def parse_report_period(date_str):
    """Преобразует строку вида 'Январь 2022' в (год, месяц_номер)."""
    parts = date_str.strip().split()
    if len(parts) != 2:
        raise ValueError(f"Неверный формат даты: {date_str}")
    month_name, year_str = parts
    month = MONTH_MAP.get(month_name.lower())
    if month is None:
        raise ValueError(f"Неизвестный месяц: {month_name}")
    year = int(year_str)
    return year, month

def convert_to_target_format(df, metric_columns):
    """
    Преобразует DataFrame в словарь вида:
    { 'Имя метрики': { год: { месяц: значение } } }
    для всех переданных метрик.
    """
    result = {}
    for col in metric_columns:
        if col not in df.columns:
            print(f"Предупреждение: столбец '{col}' не найден, пропускаем")
            continue
        # Группируем по году и месяцу, берём первое значение (если несколько записей за один месяц – можно изменить)
        temp = df.groupby(['year', 'month'])[col].first().unstack(level=0)
        year_dict = {}
        for year in temp.columns:
            month_series = temp[year].dropna()
            month_dict = {int(m): float(v) for m, v in month_series.items()}
            year_dict[int(year)] = month_dict
        result[col] = year_dict
    return result

def parse_csv(file_path):
    """Читает CSV, обрабатывает и возвращает DataFrame."""
    file_path = Path(file_path)
    if not file_path.exists():
        print(f"Файл {file_path} не найден.")
        return None

    encodings = ['utf-8', 'cp1251', 'latin1']
    df = None
    for enc in encodings:
        try:
            df = pd.read_csv(file_path, encoding=enc, sep=',', decimal='.')
            print(f"Файл успешно прочитан в кодировке {enc}")
            break
        except UnicodeDecodeError:
            continue
        except Exception as e:
            print(f"Ошибка при чтении: {e}")
            return None

    if df is None:
        print("Не удалось прочитать файл ни в одной из попыток.")
        return None

    # Преобразуем все столбцы, кроме явно строковых, в числа (если возможно)
    for col in df.columns:
        if col not in NON_METRIC_COLUMNS and col != 'report_period':
            df[col] = pd.to_numeric(df[col], errors='coerce')

    # Заполняем пропуски (например, доход банка – нулём)
    if 'доход_банка' in df.columns:
        df['доход_банка'].fillna(0, inplace=True)

    # Извлекаем год и месяц из report_period
    df[['year', 'month']] = df['report_period'].apply(
        lambda x: pd.Series(parse_report_period(x))
    )

    return df

def main():
    if len(sys.argv) > 1:
        filename = sys.argv[1]
    else:
        filename = 'transported_data.csv'

    df = parse_csv(filename)
    if df is None:
        return

    # Вывод статистики (первые строки, пропуски, описательная статистика)
    print("\nПервые 5 строк:")
    print(df.head())
    print("\nПропуски по столбцам:")
    print(df.isnull().sum())
    print("\nСтатистика по числовым столбцам:")
    print(df.describe())

    # Определяем список метрик – все столбцы, кроме служебных и добавленных year/month
    metric_columns = [col for col in df.columns
                      if col not in NON_METRIC_COLUMNS
                      and col not in ['year', 'month']]
    print(f"\nОбнаруженные метрики: {metric_columns}")

    # Преобразование в целевой формат
    target_dict = convert_to_target_format(df, metric_columns)

    print("\nРезультат преобразования (JSON):")
    print(json.dumps(target_dict, ensure_ascii=False, indent=2, default=str))

    # При желании сохранить в файл
    # with open('output.json', 'w', encoding='utf-8') as f:
    #     json.dump(target_dict, f, ensure_ascii=False, indent=2, default=str)

if __name__ == "__main__":
    main()