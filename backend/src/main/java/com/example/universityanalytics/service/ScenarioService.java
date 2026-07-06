package com.example.universityanalytics.service;

import com.example.universityanalytics.dto.*;
import com.example.universityanalytics.entity.FactEntity;
import com.example.universityanalytics.entity.ScenarioEntity;
import com.example.universityanalytics.repository.FactRepository;
import com.example.universityanalytics.repository.ScenarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);

    private final ScenarioRepository scenarioRepository;
    private final FactRepository factRepository;
    private final PythonClientService pythonClientService;
    private final GraphService graphService;
    private final ObjectMapper objectMapper;
    private static final String DEFAULT_USER = "default";

    public List<ScenarioDto> getScenarios() {
        return getScenarios(DEFAULT_USER);
    }

    public List<ScenarioDto> getScenarios(String userId) {
        return scenarioRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public ScenarioDto generateScenario(ScenarioParamsDto params) {
        return generateScenario(params, DEFAULT_USER);
    }

    @Transactional
    public ScenarioDto generateScenario(ScenarioParamsDto params, String userId) {
        return generateScenarioInternal(params, userId, 1.0);
    }

    @Transactional
    public List<ScenarioDto> generateScenarios(ScenarioParamsDto params) {
        return generateScenarios(params, DEFAULT_USER);
    }

    @Transactional
    public List<ScenarioDto> generateScenarios(ScenarioParamsDto params, String userId) {
        List<ScenarioDto> result = new ArrayList<>();

        ScenarioDto base = generateScenarioInternal(params, userId, 1.0);
        base.setKind("base");
        base.setTitle(params.getName() + " (Базовый)");
        result.add(base);

        ScenarioDto opt = generateScenarioInternal(params, userId, 1.06);
        opt.setKind("optimistic");
        opt.setTitle(params.getName() + " (Оптимистичный)");
        result.add(opt);

        ScenarioDto cons = generateScenarioInternal(params, userId, 0.95);
        cons.setKind("conservative");
        cons.setTitle(params.getName() + " (Консервативный)");
        result.add(cons);

        return result;
    }

    @Transactional
    public void deleteScenario(String id) {
        scenarioRepository.deleteById(id);
    }

    // ===== ОСНОВНАЯ ЛОГИКА =====

    private ScenarioDto generateScenarioInternal(ScenarioParamsDto params, String userId, double rateBias) {
        // 1. Последний период
        List<String> periods = factRepository.findAllPeriods();
        if (periods.isEmpty()) throw new RuntimeException("Нет данных для прогноза");
        String lastPeriod = periods.get(periods.size() - 1);
        String[] parts = lastPeriod.split("-");
        int lastYear = Integer.parseInt(parts[0]);
        int lastMonth = Integer.parseInt(parts[1]);

        int horizon = params.getHorizonMonths() != null ? params.getHorizonMonths() : 6;
        int periodFrom = params.getPeriodFrom() != null ? params.getPeriodFrom() : 1;

        // 2. Граф
        BusinessGraphDto graph = graphService.getGraph(userId);
        List<FactEntity> allFacts = factRepository.findAll();
        String[] futureMonths = getFutureMonths(lastYear, lastMonth, horizon);

        // 3. Определяем нужные показатели для целевого
        Set<String> neededIndicators = findNeededIndicators(graph, params.getTargetIndicator());
        log.info("Для целевого показателя '{}' нужны базовые: {}", params.getTargetIndicator(), neededIndicators);

        if (neededIndicators.isEmpty()) {
            throw new RuntimeException("Не найдены базовые показатели для целевого '" + params.getTargetIndicator() + "'");
        }

        // 4. Прогнозируем только нужные базовые показатели
        Map<String, List<ScenarioPointDto>> baseForecast = new HashMap<>();
        for (String indicatorName : neededIndicators) {
            Map<Integer, Map<Integer, Double>> history = buildHistory(allFacts, indicatorName);
            if (history.isEmpty()) {
                log.warn("Нет истории для показателя '{}', пропускаем", indicatorName);
                continue;
            }

            String forecastMode = params.getForecastMode() != null ? params.getForecastMode() : "best";
            long start = System.currentTimeMillis();
            log.info("Прогноз для '{}' с моделью '{}'", indicatorName, forecastMode);

            var result = pythonClientService.callPredict(null, indicatorName, horizon, forecastMode, history);
            log.info("Прогноз для '{}' завершён за {} мс", indicatorName, System.currentTimeMillis() - start);

            if (result != null && result.has("forecast")) {
                var forecast = result.path("forecast");
                List<ScenarioPointDto> points = new ArrayList<>();
                for (String month : futureMonths) {
                    double val = forecast.path(month).asDouble(0.0) * rateBias;
                    points.add(new ScenarioPointDto(month, val));
                }
                baseForecast.put(indicatorName, points);
            }
        }

        // 5. Вычисляем все показатели по графу
        Map<String, List<ScenarioPointDto>> allForecast = computeAllIndicators(
                graph, baseForecast, futureMonths);

        // 6. Целевой ряд
        List<ScenarioPointDto> targetSeries = allForecast.getOrDefault(params.getTargetIndicator(), Collections.emptyList());

        // 7. По регионам
        List<ScenarioRegionValueDto> byRegion = calculateByRegion(params.getTargetIndicator(), lastYear, lastMonth, horizon);
        for (ScenarioRegionValueDto r : byRegion) {
            r.setValue(r.getValue() * rateBias);
        }

        // 8. СКО (RMSE из лучшей модели)
        Double growthRateStd = 0.0;
        // Пытаемся получить RMSE из прогноза первого показателя
        for (String ind : neededIndicators) {
            Map<Integer, Map<Integer, Double>> history = buildHistory(allFacts, ind);
            if (history.isEmpty()) continue;
            String forecastMode = params.getForecastMode() != null ? params.getForecastMode() : "best";
            var result = pythonClientService.callPredict(null, ind, horizon, forecastMode, history);
            if (result != null && result.has("metrics")) {
                growthRateStd = result.path("metrics").path("RMSE").asDouble(0.0);
                break;
            }
        }

        // 9. Драйверы
        List<ScenarioDriverDto> drivers = buildDrivers(allForecast, params.getTargetIndicator());

        // 10. Собираем DTO
        ScenarioDto dto = new ScenarioDto();
        dto.setId(UUID.randomUUID().toString());
        dto.setKind("custom");
        dto.setTitle(params.getName());
        dto.setDescription("Прогноз по методу " + params.getMethod());
        dto.setParams(params);
        dto.setStatus("ready");
        dto.setSeries(targetSeries);
        dto.setSeriesByIndicator(allForecast);
        dto.setByRegion(byRegion);
        dto.setGrowthRateStd(growthRateStd);
        dto.setDrivers(drivers);

        // 11. Сохранить
        ScenarioEntity entity = toEntity(dto);
        entity.setUserId(userId);
        scenarioRepository.save(entity);

        return dto;
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private Set<String> findNeededIndicators(BusinessGraphDto graph, String targetIndicator) {
        Set<String> result = new HashSet<>();
        Map<String, GraphNodeDto> nodeMap = graph.getNodes().stream()
                .collect(Collectors.toMap(GraphNodeDto::getId, n -> n));
        Map<String, List<GraphEdgeDto>> incoming = new HashMap<>();
        for (GraphEdgeDto e : graph.getEdges()) {
            incoming.computeIfAbsent(e.getTarget(), k -> new ArrayList<>()).add(e);
        }

        String targetId = nodeMap.entrySet().stream()
                .filter(e -> e.getValue().getIndicator().equals(targetIndicator))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (targetId == null) return result;

        collectNeeded(targetId, nodeMap, incoming, result);
        return result;
    }

    private void collectNeeded(String nodeId, Map<String, GraphNodeDto> nodeMap,
                               Map<String, List<GraphEdgeDto>> incoming, Set<String> result) {
        GraphNodeDto node = nodeMap.get(nodeId);
        if (node == null) return;
        if (!incoming.containsKey(nodeId) || incoming.get(nodeId).isEmpty()) {
            result.add(node.getIndicator());
            return;
        }
        for (GraphEdgeDto edge : incoming.get(nodeId)) {
            collectNeeded(edge.getSource(), nodeMap, incoming, result);
        }
    }

    private Map<String, List<ScenarioPointDto>> computeAllIndicators(
            BusinessGraphDto graph,
            Map<String, List<ScenarioPointDto>> baseForecast,
            String[] futureMonths) {

        Map<String, GraphNodeDto> nodeMap = graph.getNodes().stream()
                .collect(Collectors.toMap(GraphNodeDto::getId, n -> n));

        Map<String, List<GraphEdgeDto>> incoming = new HashMap<>();
        for (GraphEdgeDto e : graph.getEdges()) {
            incoming.computeIfAbsent(e.getTarget(), k -> new ArrayList<>()).add(e);
        }

        Set<String> baseIds = nodeMap.entrySet().stream()
                .filter(e -> "indicator".equals(e.getValue().getKind()))
                .filter(e -> !incoming.containsKey(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        class Evaluator {
            double evaluate(String nodeId, String month, Set<String> visited) {
                if (visited.contains(nodeId)) return 0.0;
                visited.add(nodeId);
                GraphNodeDto node = nodeMap.get(nodeId);
                if (node == null) return 0.0;
                if (baseIds.contains(nodeId) || !incoming.containsKey(nodeId) || incoming.get(nodeId).isEmpty()) {
                    List<ScenarioPointDto> pts = baseForecast.get(node.getIndicator());
                    if (pts != null) {
                        return pts.stream().filter(p -> p.getPeriod().equals(month))
                                .map(ScenarioPointDto::getValue).findFirst().orElse(0.0);
                    }
                    return 0.0;
                }
                List<GraphEdgeDto> edges = incoming.get(nodeId);
                if (edges == null || edges.isEmpty()) return 0.0;
                double result = 0.0;
                boolean first = true;
                for (GraphEdgeDto edge : edges) {
                    double val = evaluate(edge.getSource(), month, new HashSet<>(visited));
                    if (first) {
                        result = val;
                        first = false;
                    } else {
                        switch (edge.getOperator()) {
                            case "+": result += val; break;
                            case "-": result -= val; break;
                            case "*": result *= val; break;
                            case "/": if (val != 0) result /= val; break;
                            case "%": if (val != 0) result %= val; break;
                        }
                    }
                }
                return result;
            }
        }

        Evaluator evaluator = new Evaluator();
        Map<String, List<ScenarioPointDto>> allForecast = new LinkedHashMap<>();
        for (GraphNodeDto node : graph.getNodes()) {
            if (!"indicator".equals(node.getKind())) continue;
            List<ScenarioPointDto> points = new ArrayList<>();
            for (String month : futureMonths) {
                double val = evaluator.evaluate(node.getId(), month, new HashSet<>());
                points.add(new ScenarioPointDto(month, val));
            }
            allForecast.put(node.getIndicator(), points);
        }
        return allForecast;
    }

    private Map<Integer, Map<Integer, Double>> buildHistory(List<FactEntity> facts, String indicator) {
        Map<Integer, Map<Integer, Double>> history = new LinkedHashMap<>();
        for (FactEntity f : facts) {
            if (!f.getIndicator().equals(indicator)) continue;
            String[] parts = f.getPeriod().split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            history.computeIfAbsent(year, k -> new LinkedHashMap<>())
                    .merge(month, f.getValue(), Double::sum);
        }
        return history;
    }

    private String[] getFutureMonths(int year, int month, int count) {
        String[] months = new String[count];
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        for (int i = 0; i < count; i++) {
            cal.add(Calendar.MONTH, 1);
            months[i] = String.format("%d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
        }
        return months;
    }

    private List<ScenarioRegionValueDto> calculateByRegion(String indicator, int year, int month, int horizon) {
        List<FactEntity> facts = factRepository.findAll();
        String lastPeriod = String.format("%d-%02d", year, month);
        Map<String, Double> regionLastValue = new HashMap<>();
        for (FactEntity f : facts) {
            if (f.getIndicator().equals(indicator) && f.getPeriod().equals(lastPeriod)) {
                regionLastValue.put(f.getSubject(), f.getValue());
            }
        }
        double total = regionLastValue.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total == 0) return Collections.emptyList();

        List<ScenarioRegionValueDto> list = new ArrayList<>();
        for (Map.Entry<String, Double> entry : regionLastValue.entrySet()) {
            ScenarioRegionValueDto dto = new ScenarioRegionValueDto();
            dto.setSubject(entry.getKey());
            dto.setValue(entry.getValue() * 1.05);
            list.add(dto);
        }
        return list;
    }

    private List<ScenarioDriverDto> buildDrivers(Map<String, List<ScenarioPointDto>> forecast, String target) {
        List<ScenarioDriverDto> drivers = new ArrayList<>();
        for (Map.Entry<String, List<ScenarioPointDto>> entry : forecast.entrySet()) {
            if (entry.getKey().equals(target)) continue;
            ScenarioDriverDto dto = new ScenarioDriverDto();
            dto.setIndicator(entry.getKey());
            double avg = entry.getValue().stream().mapToDouble(ScenarioPointDto::getValue).average().orElse(0);
            dto.setValue(avg);
            dto.setUnit("руб");
            dto.setContributionPct(5.0);
            drivers.add(dto);
        }
        drivers.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return drivers.stream().limit(5).collect(Collectors.toList());
    }

    private ScenarioEntity toEntity(ScenarioDto dto) {
        try {
            ScenarioEntity entity = new ScenarioEntity();
            entity.setId(dto.getId());
            entity.setName(dto.getTitle());
            entity.setParamsJson(objectMapper.writeValueAsString(dto.getParams()));
            entity.setResultJson(objectMapper.writeValueAsString(dto));
            entity.setCreatedAt(LocalDateTime.now());
            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ScenarioDto fromEntity(ScenarioEntity entity) {
        try {
            return objectMapper.readValue(entity.getResultJson(), ScenarioDto.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}