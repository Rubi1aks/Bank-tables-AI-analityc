package com.example.universityanalytics.service;

import com.example.universityanalytics.dto.ScenarioResponse;
import com.example.universityanalytics.entity.FactEntity;
import com.example.universityanalytics.repository.FactRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);

    private final FactRepository factRepository;
    private final PythonClientService pythonClientService;

    public ScenarioService(FactRepository factRepository, PythonClientService pythonClientService) {
        this.factRepository = factRepository;
        this.pythonClientService = pythonClientService;
    }

    /**
     * Получить 4 стандартных сценария (BASELINE, OPTIMISTIC, CONSERVATIVE, AI)
     */
    public List<ScenarioResponse> getDefaultScenarios(String subject, int horizonMonths) {
        List<FactEntity> history = factRepository.findBySubject(subject);
        if (history.isEmpty()) {
            return generateFallbackScenarios(subject);
        }

        Map<String, Object> historyData = prepareHistoryForPython(history);

        Map<String, String> scenarioTypes = Map.of(
                "BASELINE", "baseline",
                "OPTIMISTIC", "optimistic",
                "CONSERVATIVE", "conservative",
                "AI", "ai"
        );

        List<ScenarioResponse> scenarios = new ArrayList<>();
        for (Map.Entry<String, String> entry : scenarioTypes.entrySet()) {
            String scenarioType = entry.getKey();
            String method = entry.getValue();

            JsonNode result = pythonClientService.callPredict(subject, horizonMonths, method, historyData);
            if (result != null && result.has("points")) {
                scenarios.add(convertToScenarioResponse(scenarioType, result));
            } else {
                log.warn("Python не ответил для метода {}, используем fallback", method);
                scenarios.add(createFallbackScenario(scenarioType, subject));
            }
        }
        return scenarios;
    }

    /**
     * Кастомный сценарий (пользовательский)
     */
    public ScenarioResponse computeCustomScenario(String subject, int horizonMonths, String method,
                                                  Map<String, Double> multipliers) {
        List<FactEntity> history = factRepository.findBySubject(subject);
        if (history.isEmpty()) {
            return createFallbackScenario("CUSTOM", subject);
        }

        Map<String, Object> historyData = prepareHistoryForPython(history);
        // Добавляем множители драйверов
        if (multipliers != null && !multipliers.isEmpty()) {
            historyData.put("multipliers", multipliers);
        }

        JsonNode result = pythonClientService.callPredict(subject, horizonMonths, method, historyData);
        if (result != null && result.has("points")) {
            return convertToScenarioResponse("CUSTOM", result);
        }
        return createFallbackScenario("CUSTOM", subject);
    }

    // ---------- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ----------

    /**
     * Преобразует список FactEntity в формат для Python:
     * { "history": { 2025: {1: 1000, 2: 1100, ...}, 2026: {...} } }
     */
    private Map<String, Object> prepareHistoryForPython(List<FactEntity> history) {
        // Группируем по периоду и индикатору
        Map<String, Map<String, Double>> periodMap = new LinkedHashMap<>();
        for (FactEntity f : history) {
            periodMap.computeIfAbsent(f.getPeriod(), k -> new HashMap<>())
                    .put(f.getIndicator(), f.getValue());
        }

        // Превращаем в словарь {год: {месяц: значение_дохода}}
        Map<Integer, Map<Integer, Double>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : periodMap.entrySet()) {
            String period = entry.getKey();
            String[] parts = period.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            Double income = entry.getValue().get("Доход банка");
            if (income != null) {
                result.computeIfAbsent(year, k -> new HashMap<>()).put(month, income);
            }
        }
        return Map.of("history", result);
    }

    /**
     * Преобразует JSON-ответ от Python в ScenarioResponse
     */
    private ScenarioResponse convertToScenarioResponse(String scenarioType, JsonNode pythonResult) {
        ScenarioResponse response = new ScenarioResponse();
        response.setScenarioType(scenarioType);
        response.setDescription(pythonResult.path("description").asText("Прогноз"));

        List<ScenarioResponse.ScenarioPoint> points = new ArrayList<>();
        JsonNode pointsNode = pythonResult.path("points");
        for (JsonNode p : pointsNode) {
            ScenarioResponse.ScenarioPoint point = new ScenarioResponse.ScenarioPoint();
            point.setPeriod(p.path("period").asText());
            point.setValue(p.path("value").asDouble());
            point.setLowerBound(p.path("lowerBound").asDouble());
            point.setUpperBound(p.path("upperBound").asDouble());
            points.add(point);
        }
        response.setPoints(points);
        response.setQualityScore(pythonResult.path("qualityScore").asDouble(0.12));
        return response;
    }

    /**
     * Fallback-заглушки (если Python недоступен)
     */
    private List<ScenarioResponse> generateFallbackScenarios(String subject) {
        List<ScenarioResponse> fallback = new ArrayList<>();
        fallback.add(createFallbackScenario("BASELINE", subject));
        fallback.add(createFallbackScenario("OPTIMISTIC", subject));
        fallback.add(createFallbackScenario("CONSERVATIVE", subject));
        fallback.add(createFallbackScenario("AI", subject));
        return fallback;
    }

    private ScenarioResponse createFallbackScenario(String type, String subject) {
        List<ScenarioResponse.ScenarioPoint> points = new ArrayList<>();
        String[] months = {"2025-07", "2025-08", "2025-09", "2025-10", "2025-11", "2025-12"};
        double base = 30000;
        double factor = switch (type) {
            case "OPTIMISTIC" -> 1.2;
            case "CONSERVATIVE" -> 0.85;
            case "AI" -> 1.05;
            default -> 1.0;
        };
        for (int i = 0; i < months.length; i++) {
            double income = base * factor * (1 + i * 0.02);
            points.add(new ScenarioResponse.ScenarioPoint(
                    months[i],
                    income,
                    income * 0.85,
                    income * 1.15
            ));
        }
        return new ScenarioResponse(type, "Fallback для " + subject + " (" + type + ")", points, 0.12);
    }
}