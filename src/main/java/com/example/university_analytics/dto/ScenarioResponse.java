package com.example.universityanalytics.dto;

import java.util.List;

public class ScenarioResponse {

    private String scenarioType;
    private String description;
    private List<ScenarioPoint> points;
    private Double qualityScore;

    public ScenarioResponse() {}

    public ScenarioResponse(String scenarioType, String description, List<ScenarioPoint> points, Double qualityScore) {
        this.scenarioType = scenarioType;
        this.description = description;
        this.points = points;
        this.qualityScore = qualityScore;
    }

    public String getScenarioType() { return scenarioType; }
    public void setScenarioType(String scenarioType) { this.scenarioType = scenarioType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<ScenarioPoint> getPoints() { return points; }
    public void setPoints(List<ScenarioPoint> points) { this.points = points; }

    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }

    public static class ScenarioPoint {
        private String period;
        private Double bankIncome;
        private Double arpu;
        private Double penetrationPct;
        private Double lowerBound;
        private Double upperBound;

        public ScenarioPoint() {}

        public ScenarioPoint(String period, Double bankIncome, Double arpu, Double penetrationPct,
                             Double lowerBound, Double upperBound) {
            this.period = period;
            this.bankIncome = bankIncome;
            this.arpu = arpu;
            this.penetrationPct = penetrationPct;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }

        public Double getBankIncome() { return bankIncome; }
        public void setBankIncome(Double bankIncome) { this.bankIncome = bankIncome; }

        public Double getArpu() { return arpu; }
        public void setArpu(Double arpu) { this.arpu = arpu; }

        public Double getPenetrationPct() { return penetrationPct; }
        public void setPenetrationPct(Double penetrationPct) { this.penetrationPct = penetrationPct; }

        public Double getLowerBound() { return lowerBound; }
        public void setLowerBound(Double lowerBound) { this.lowerBound = lowerBound; }

        public Double getUpperBound() { return upperBound; }
        public void setUpperBound(Double upperBound) { this.upperBound = upperBound; }
    }
}