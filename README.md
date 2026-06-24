# University Analytics — Backend MVP

> **Запуск:** `./gradlew bootRun`  
> **Порт:** `http://localhost:8080`

---

## Все эндпоинты

| Метод | Эндпоинт | Что показывает |
|-------|----------|----------------|
| GET | `/api/facts` | Все данные |
| GET | `/api/facts?subject=Москва` | Данные по региону |
| GET | `/api/regions` | Список регионов |
| GET | `/api/scenarios?subject=Москва` | **4 сценария** (BASELINE, OPTIMISTIC, CONSERVATIVE, AI) |
| GET | `/api/graph?subject=Москва` | **Граф зависимостей** |
| GET | `/api/seasonality?subject=Москва` | **Сезонность** (12 месяцев) |
| GET | `/api/news?subject=Москва` | Новости (заглушка) |
| GET | `/api/anomalies?subject=Москва` | Аномалии (заглушка) |
| POST | `/api/upload` | Загрузка файлов |
| GET | `/api/export/csv` | Экспорт в CSV |

---

## Swagger 
http://localhost:8080/swagger-ui.html

---

## H2 Console 
http://localhost:8080/h2-console

- **JDBC URL:** `jdbc:h2:file:./data/university_db`
- **User:** `sa`
- **Password:** (пусто)

---

## 🔌 WebSocket 
ws://localhost:8080/ws
/topic/upload/{uploadId}
