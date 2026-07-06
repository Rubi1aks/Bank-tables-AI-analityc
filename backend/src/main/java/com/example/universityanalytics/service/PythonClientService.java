package com.example.universityanalytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class PythonClientService {

    private static final Logger log = LoggerFactory.getLogger(PythonClientService.class);

    @Value("${python.service.url:http://localhost:5000}")
    private String pythonUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PythonClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public JsonNode callPredict(String subject, String indicator, int horizon, String method,
                                Map<Integer, Map<Integer, Double>> history) {
        String url = pythonUrl + "/predict";

        try {
            ObjectNode request = objectMapper.createObjectNode();
            ObjectNode dataNode = objectMapper.createObjectNode();
            ObjectNode indicatorNode = objectMapper.createObjectNode();

            for (Map.Entry<Integer, Map<Integer, Double>> yearEntry : history.entrySet()) {
                int year = yearEntry.getKey();
                ObjectNode monthNode = objectMapper.createObjectNode();
                for (Map.Entry<Integer, Double> monthEntry : yearEntry.getValue().entrySet()) {
                    monthNode.put(String.valueOf(monthEntry.getKey()), monthEntry.getValue());
                }
                indicatorNode.set(String.valueOf(year), monthNode);
            }

            dataNode.set(indicator, indicatorNode);
            request.set("data", dataNode);
            request.put("n_periods", horizon);

            if (method != null && !method.isEmpty() && !"best".equals(method) && !"all".equals(method)) {
                request.put("method", method);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            long start = System.currentTimeMillis();
            log.info("Отправка запроса в Python для '{}' с методом '{}'", indicator, method != null ? method : "best");

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            log.info("Python ответ для '{}' за {} мс, статус {}", indicator, System.currentTimeMillis() - start, response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode result = objectMapper.readTree(response.getBody());
                JsonNode metricResult = result.path(indicator);
                if (metricResult.has("models")) {
                    JsonNode models = metricResult.path("models");
                    if (method != null && !method.isEmpty() && !"best".equals(method) && !"all".equals(method)) {
                        for (JsonNode model : models) {
                            String modelName = model.path("name").asText("");
                            String mappedName = mapModelName(modelName);
                            if (method.equals(mappedName) || modelName.equalsIgnoreCase(method)) {
                                log.info("Найдена модель '{}' для '{}'", modelName, indicator);
                                return model;
                            }
                        }
                        log.warn("Модель '{}' не найдена для '{}', берём лучшую", method, indicator);
                        for (JsonNode model : models) {
                            if (model.path("best").asBoolean(false)) {
                                return model;
                            }
                        }
                        if (models.size() > 0) {
                            return models.get(0);
                        }
                    }
                    return metricResult;
                }
                return result;
            } else {
                log.error("Python вернул ошибку: {}", response.getStatusCode());
                log.error("Тело ответа: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("Ошибка при вызове Python для '{}': {}", indicator, e.getMessage());
        }
        return null;
    }

    private String mapModelName(String pythonName) {
        if (pythonName == null) return null;
        String lower = pythonName.toLowerCase();
        if (lower.contains("sarimax")) return "sarimax";
        if (lower.contains("prophet")) return "prophet";
        if (lower.contains("exponential") || lower.contains("smoothing")) return "exponential_smoothing";
        if (lower.contains("stl")) return "stl";
        if (lower.contains("ridge")) return "ridge";
        if (lower.contains("croston")) return "croston";
        return pythonName;
    }

    public JsonNode callGenerateText(Map<String, Object> data) {
        String url = pythonUrl + "/generate-text";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("Python generate-text error: {}", e.getMessage());
        }
        return null;
    }

    public JsonNode callGenerateNews(Map<String, Object> data) {
        String url = pythonUrl + "/generate-news";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("Python generate-news error: {}", e.getMessage());
        }
        return null;
    }
}