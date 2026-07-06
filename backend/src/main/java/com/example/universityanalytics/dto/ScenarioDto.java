package com.example.universityanalytics.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ScenarioDto {
    private String id;
    private String kind;
    private String title;
    private String description;
    private ScenarioParamsDto params;
    private String status;
    private List<ScenarioPointDto> series;
    private Map<String, List<ScenarioPointDto>> seriesByIndicator;
    private List<ScenarioRegionValueDto> byRegion;
    private Double growthRateStd;
    private List<ScenarioDriverDto> drivers;
}