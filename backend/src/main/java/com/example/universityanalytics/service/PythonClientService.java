package com.example.universityanalytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class PythonClientService {

    private static final Logger log = LoggerFactory.getLogger(PythonClientService.class);

    @Value("${python.service.url:http://localhost:5000}")
    private String pythonUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode callPredict(String subject, int horizon, String method, Map<String, Object> data) {
        String url = pythonUrl + "/predict";
        Map<String, Object> request = new HashMap<>();
        request.put("subject", subject);
        request.put("horizon", horizon);
        request.put("method", method);
        request.put("data", data); // исторические данные

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readTree(response.getBody());
            } else {
                log.error("Python service returned error: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error calling Python service", e);
            return null;
        }
    }

    public JsonNode callAnomalies(String subject) {
        String url = pythonUrl + "/anomalies?subject=" + subject;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("Error calling anomalies", e);
        }
        return null;
    }

    public JsonNode callNews(String subject) {
        String url = pythonUrl + "/news?subject=" + subject;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("Error calling news", e);
        }
        return null;
    }

    public JsonNode callSummary(String subject) {
        String url = pythonUrl + "/summary?subject=" + subject;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("Error calling summary", e);
        }
        return null;
    }
}