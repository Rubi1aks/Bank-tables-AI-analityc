package com.example.universityanalytics.service;

import com.example.universityanalytics.entity.FactEntity;
import com.example.universityanalytics.repository.FactRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DriversService {

    private final FactRepository factRepository;

    public DriversService(FactRepository factRepository) {
        this.factRepository = factRepository;
    }

    public List<DriverRow> getDrivers(String subject) {
        List<FactEntity> facts = factRepository.findBySubject(subject);
        if (facts.isEmpty()) return Collections.emptyList();

        // Группируем по периоду
        Map<String, Map<String, Double>> periodMap = new LinkedHashMap<>();
        for (FactEntity f : facts) {
            periodMap.computeIfAbsent(f.getPeriod(), k -> new HashMap<>())
                    .put(f.getIndicator(), f.getValue());
        }

        // Сортируем периоды по дате
        List<String> sortedPeriods = new ArrayList<>(periodMap.keySet());
        Collections.sort(sortedPeriods);

        List<DriverRow> result = new ArrayList<>();
        Double previousIncome = null;

        for (String period : sortedPeriods) {
            Map<String, Double> indicators = periodMap.get(period);

            Double bankIncome = indicators.get("Доход банка");
            Double clients = indicators.get("Количество клиентов");
            Double totalPeople = indicators.get("Общее количество людей");
            Double txVolume = indicators.get("Объем транзакций");

            Double arpu = (clients != null && clients > 0 && bankIncome != null) ? bankIncome / clients : null;
            Double penetration = (totalPeople != null && totalPeople > 0 && clients != null) ? (clients / totalPeople) * 100 : null;
            Double avgCheck = (clients != null && clients > 0 && txVolume != null) ? txVolume / clients : null;

            DriverRow row = new DriverRow();
            row.setReportPeriod(period + "-01"); // фронт ждёт YYYY-MM-DD
            row.setSubjectRf(subject);
            row.setAvgArpu(arpu != null ? Math.round(arpu * 100.0) / 100.0 : null);
            row.setMarketPenetrationPct(penetration != null ? Math.round(penetration * 100.0) / 100.0 : null);
            row.setAvgTransactionCheck(avgCheck != null ? Math.round(avgCheck * 100.0) / 100.0 : null);
            row.setBankIncomeLag1(previousIncome);
            result.add(row);

            previousIncome = bankIncome;
        }
        return result;
    }

    public static class DriverRow {
        private String reportPeriod;
        private String subjectRf;
        private Double avgArpu;
        private Double marketPenetrationPct;
        private Double avgTransactionCheck;
        private Double bankIncomeLag1;

        // геттеры и сеттеры
        public String getReportPeriod() { return reportPeriod; }
        public void setReportPeriod(String reportPeriod) { this.reportPeriod = reportPeriod; }
        public String getSubjectRf() { return subjectRf; }
        public void setSubjectRf(String subjectRf) { this.subjectRf = subjectRf; }
        public Double getAvgArpu() { return avgArpu; }
        public void setAvgArpu(Double avgArpu) { this.avgArpu = avgArpu; }
        public Double getMarketPenetrationPct() { return marketPenetrationPct; }
        public void setMarketPenetrationPct(Double marketPenetrationPct) { this.marketPenetrationPct = marketPenetrationPct; }
        public Double getAvgTransactionCheck() { return avgTransactionCheck; }
        public void setAvgTransactionCheck(Double avgTransactionCheck) { this.avgTransactionCheck = avgTransactionCheck; }
        public Double getBankIncomeLag1() { return bankIncomeLag1; }
        public void setBankIncomeLag1(Double bankIncomeLag1) { this.bankIncomeLag1 = bankIncomeLag1; }
    }
}