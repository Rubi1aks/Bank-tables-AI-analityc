package com.example.universityanalytics.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ScenarioParamsDto {
    private String name;
    private String targetIndicator;
    private Integer periodFrom;
    private Integer horizonMonths;
    private Boolean seasonality;
    private String method;
    private Map<String, Double> driverMultipliers;
    private String forecastMode = "best";
}