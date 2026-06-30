package com.example.universityanalytics.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class PythonClientService {

    private final WebClient webClient;

    public PythonClientService() {
        this.webClient = WebClient.builder()
                .baseUrl("http://localhost:5000")
                .build();
    }

    public String callPredict(String subject, int horizon, String method) {
        return webClient.post()
                .uri("/predict")
                .bodyValue(Map.of(
                        "subject", subject,
                        "horizon", horizon,
                        "method", method
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String callAnomalies(String subject) {
        return webClient.get()
                .uri("/anomalies?subject=" + subject)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String callNews(String subject) {
        return webClient.get()
                .uri("/news?subject=" + subject)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}