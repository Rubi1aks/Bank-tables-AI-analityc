package com.example.universityanalytics.service;

import com.example.universityanalytics.dto.ScenarioResponse;
import com.example.universityanalytics.entity.BankAnalyticsEntity;
import com.example.universityanalytics.repository.BankAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ScenarioService {

    private final BankAnalyticsRepository repository;

    public ScenarioService(BankAnalyticsRepository repository) {
        this.repository = repository;
    }

    public List<ScenarioResponse> generateScenarios(String subject) {
        List<BankAnalyticsEntity> data = repository.findBySubject(subject);

        if (data.isEmpty()) {
            return createFallbackScenarios(subject);
        }

        data.sort(Comparator.comparing(BankAnalyticsEntity::getPeriod));

        double lastIncome = data.get(data.size() - 1).getBankIncome();
        double growthRate = calculateGrowthRate(data);

        List<ScenarioResponse> scenarios = new ArrayList<>();

        scenarios.add(createScenario("BASELINE", "Продление текущих трендов", data, lastIncome, growthRate, 0.12));
        scenarios.add(createScenario("OPTIMISTIC", "Рост ключевых драйверов +20%", data, lastIncome, growthRate * 1.2, 0.08));
        scenarios.add(createScenario("CONSERVATIVE", "Снижение рыночной активности", data, lastIncome, growthRate * 0.85, 0.21));
        scenarios.add(createAIScenario(data, lastIncome, growthRate));

        return scenarios;
    }

    private ScenarioResponse createScenario(String type, String desc, List<BankAnalyticsEntity> data,
                                            double lastIncome, double growthRate, double qualityScore) {
        List<ScenarioResponse.ScenarioPoint> points = new ArrayList<>();

        String lastPeriod = data.get(data.size() - 1).getPeriod();
        String[] parts = lastPeriod.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);

        double currentIncome = lastIncome;

        for (int i = 1; i <= 6; i++) {
            month++;
            if (month > 12) { month = 1; year++; }
            String period = String.format("%d-%02d", year, month);

            double seasonalFactor = getSeasonalFactor(month);
            double income = currentIncome * (1 + growthRate) * seasonalFactor;
            double stdDev = qualityScore;
            double lowerBound = income * (1 - stdDev);
            double upperBound = income * (1 + stdDev);

            double avgArpu = 13.6 * (1 + growthRate * 0.5 * i / 6);
            double penetration = 67.0 * (1 + growthRate * 0.3 * i / 6);

            points.add(new ScenarioResponse.ScenarioPoint(period, income, avgArpu, penetration, lowerBound, upperBound));
            currentIncome = income;
        }

        return new ScenarioResponse(type, desc, points, qualityScore);
    }

    private ScenarioResponse createAIScenario(List<BankAnalyticsEntity> data, double lastIncome, double growthRate) {
        List<ScenarioResponse.ScenarioPoint> points = new ArrayList<>();

        String lastPeriod = data.get(data.size() - 1).getPeriod();
        String[] parts = lastPeriod.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);

        double currentIncome = lastIncome;
        double aiGrowthRate = growthRate * 1.05;

        for (int i = 1; i <= 6; i++) {
            month++;
            if (month > 12) { month = 1; year++; }
            String period = String.format("%d-%02d", year, month);

            double seasonalFactor = getSeasonalFactor(month);
            double income = currentIncome * (1 + aiGrowthRate) * seasonalFactor;
            double stdDev = 0.09;
            double lowerBound = income * (1 - stdDev);
            double upperBound = income * (1 + stdDev);

            double avgArpu = 13.6 * (1 + aiGrowthRate * 0.3);
            double penetration = 67.0 * (1 + aiGrowthRate * 0.2);

            points.add(new ScenarioResponse.ScenarioPoint(period, income, avgArpu, penetration, lowerBound, upperBound));
            currentIncome = income;
        }

        return new ScenarioResponse("AI", "Автоматически предложенный ИИ с учетом сезонности", points, 0.09);
    }

    private double calculateGrowthRate(List<BankAnalyticsEntity> data) {
        if (data.size() < 2) return 0.01;
        double first = data.get(0).getBankIncome();
        double last = data.get(data.size() - 1).getBankIncome();
        return (last / first) / (data.size() - 1) - 1;
    }

    private double getSeasonalFactor(int month) {
        double[] factors = {0.95, 1.02, 1.05, 1.00, 0.95, 0.70, 0.30, 0.30, 1.20, 1.15, 1.10, 1.05};
        return factors[month - 1];
    }

    private List<ScenarioResponse> createFallbackScenarios(String subject) {
        List<ScenarioResponse> fallback = new ArrayList<>();
        List<ScenarioResponse.ScenarioPoint> points = new ArrayList<>();
        String[] months = {"2025-07", "2025-08", "2025-09", "2025-10", "2025-11", "2025-12"};
        double base = 30000;

        for (int i = 0; i < months.length; i++) {
            double income = base * (1 + i * 0.02);
            double stdDev = 0.12;
            points.add(new ScenarioResponse.ScenarioPoint(
                    months[i],
                    income,
                    13.6 * (1 + i * 0.01),
                    67.0 * (1 + i * 0.005),
                    income * (1 - stdDev),
                    income * (1 + stdDev)
            ));
        }
        fallback.add(new ScenarioResponse("BASELINE", "Нет данных, сгенерирована заглушка для " + subject, points, 0.12));
        return fallback;
    }
}