package com.example.universityanalytics.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ModelForecastDto {
    private String name;
    private Integer rank;
    private Map<String, Double> metrics;
    private Map<String, Double> forecast;
}