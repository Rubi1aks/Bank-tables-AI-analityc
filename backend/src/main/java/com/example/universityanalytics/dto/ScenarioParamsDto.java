package com.example.universityanalytics.dto;

import lombok.Data;
import java.util.*;

@Data
public class ScenarioParamsDto {
    private String name;
    private String targetIndicator;
    private List<String> regions;
    private Integer horizonMonths;
    private Boolean useFormulas = true;
    private Boolean useDirectForecast = false;
    private String method;
    private Boolean seasonality;
    private Map<String, Double> driverMultipliers;
    private String forecastMode = "best";
}