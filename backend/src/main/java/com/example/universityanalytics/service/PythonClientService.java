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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode callPredict(String subject, String indicator, int horizon, String method,
                                Map<Integer, Map<Integer, Double>> history) {
        String url = pythonUrl + "/predict";

        ObjectNode request = objectMapper.createObjectNode();
        request.put("subject", subject);
        request.put("indicator", indicator);
        request.put("horizon", horizon);
        request.put("method", method);
        request.set("history", objectMapper.valueToTree(history));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("Python service error: {}", e.getMessage());
        }
        return null;
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