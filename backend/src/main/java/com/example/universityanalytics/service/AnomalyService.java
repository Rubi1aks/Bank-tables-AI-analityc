package com.example.universityanalytics.service;

import com.example.universityanalytics.dto.AnomalyDto;
import com.example.universityanalytics.entity.AnomalyCorrectionEntity;
import com.example.universityanalytics.entity.FactEntity;
import com.example.universityanalytics.repository.AnomalyCorrectionRepository;
import com.example.universityanalytics.repository.FactRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnomalyService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);
    private static final String DEFAULT_USER = "default";

    private final FactRepository factRepository;
    private final AnomalyCorrectionRepository correctionRepository;

    private List<FactEntity> cachedFacts = null;
    private final Object cacheLock = new Object();

    private Map<String, Map<Integer, List<Double>>> cachedGroups = null;
    private final Object groupsLock = new Object();

    private void loadCache() {
        if (cachedFacts == null) {
            synchronized (cacheLock) {
                if (cachedFacts == null) {
                    cachedFacts = factRepository.findAll();
                    log.info("Загружено {} фактов в кэш", cachedFacts.size());
                }
            }
        }
        if (cachedGroups == null) {
            synchronized (groupsLock) {
                if (cachedGroups == null) {
                    buildGroupCache(cachedFacts);
                }
            }
        }
    }

    private void buildGroupCache(List<FactEntity> facts) {
        cachedGroups = new ConcurrentHashMap<>();
        for (FactEntity f : facts) {
            String key = f.getIndicator() + "|" + f.getSubject();
            int month = Integer.parseInt(f.getPeriod().split("-")[1]);
            cachedGroups.computeIfAbsent(key, k -> new HashMap<>())
                    .computeIfAbsent(month, m -> new ArrayList<>())
                    .add(f.getValue());
        }
        for (Map<Integer, List<Double>> monthMap : cachedGroups.values()) {
            for (List<Double> vals : monthMap.values()) {
                Collections.sort(vals);
            }
        }
        log.info("Построен кэш групп, всего групп: {}", cachedGroups.size());
    }
    private void invalidateCache() {
        synchronized (cacheLock) {
            cachedFacts = null;
        }
        synchronized (groupsLock) {
            cachedGroups = null;
        }
        log.info("Кэш аномалий инвалидирован");
    }
    public List<AnomalyDto> detectAnomalies(String subject, String indicator, Double threshold) {
        loadCache();

        double k = threshold != null ? threshold : 2.5;
        List<AnomalyDto> anomalies = new ArrayList<>();

        for (String key : cachedGroups.keySet()) {
            String[] parts = key.split("\\|");
            String ind = parts[0];
            String subj = parts[1];
            if (subject != null && !subject.isEmpty() && !subj.equals(subject)) continue;
            if (indicator != null && !indicator.isEmpty() && !ind.equals(indicator)) continue;

            Map<Integer, List<Double>> monthMap = cachedGroups.get(key);
            if (monthMap.size() < 5) continue;

            for (Map.Entry<Integer, List<Double>> entry : monthMap.entrySet()) {
                int month = entry.getKey();
                List<Double> sortedVals = entry.getValue();
                if (sortedVals.size() < 5) continue;

                double q1 = percentile(sortedVals, 0.25);
                double q3 = percentile(sortedVals, 0.75);
                double iqr = q3 - q1;
                double low = q1 - k * iqr;
                double high = q3 + k * iqr;

                List<Double> cleanVals = new ArrayList<>();
                for (double v : sortedVals) {
                    if (v >= low && v <= high) cleanVals.add(v);
                }
                double cleanMedian;
                if (!cleanVals.isEmpty()) {
                    Collections.sort(cleanVals);
                    cleanMedian = cleanVals.size() % 2 == 0 ?
                            (cleanVals.get(cleanVals.size() / 2 - 1) + cleanVals.get(cleanVals.size() / 2)) / 2.0 :
                            cleanVals.get(cleanVals.size() / 2);
                } else {
                    cleanMedian = sortedVals.size() % 2 == 0 ?
                            (sortedVals.get(sortedVals.size() / 2 - 1) + sortedVals.get(sortedVals.size() / 2)) / 2.0 :
                            sortedVals.get(sortedVals.size() / 2);
                }

                for (FactEntity f : cachedFacts) {
                    if (!f.getIndicator().equals(ind) || !f.getSubject().equals(subj)) continue;
                    int factMonth = Integer.parseInt(f.getPeriod().split("-")[1]);
                    if (factMonth != month) continue;

                    double val = f.getValue();
                    if (val < low || val > high) {
                        AnomalyDto dto = new AnomalyDto();
                        dto.setId(f.getSubject() + "-" + f.getIndicator() + "-" + f.getPeriod());
                        dto.setPeriod(f.getPeriod());
                        dto.setIndicator(f.getIndicator());
                        dto.setSubject(f.getSubject());
                        double deviation = cleanMedian != 0 ? ((val - cleanMedian) / cleanMedian) * 100 : 0;
                        dto.setDeviationPct(deviation);
                        dto.setDirection(val > cleanMedian ? "up" : "down");
                        dto.setText(String.format("%s: %s в %s %s на %.1f%% от нормы",
                                f.getPeriod(), f.getIndicator(), f.getSubject(),
                                val > cleanMedian ? "вырос" : "снизился", Math.abs(deviation)));
                        anomalies.add(dto);
                    }
                }
            }
        }
        return anomalies;
    }

    @Transactional
    public int replaceAnomalies(String subject, String indicator, Double threshold, String userId) {
        if (userId == null) userId = DEFAULT_USER;
        if (correctionRepository.existsByUserId(userId)) {
            throw new IllegalStateException("Аномалии уже были заменены. Сначала восстановите исходные данные.");
        }
        invalidateCache();
        List<FactEntity> allFacts = factRepository.findAll();
        if (subject != null && !subject.isEmpty()) {
            allFacts = allFacts.stream().filter(f -> f.getSubject().equals(subject)).collect(Collectors.toList());
        }
        if (indicator != null && !indicator.isEmpty()) {
            allFacts = allFacts.stream().filter(f -> f.getIndicator().equals(indicator)).collect(Collectors.toList());
        }
        if (allFacts.isEmpty()) return 0;

        Map<String, List<FactEntity>> groups = allFacts.stream()
                .collect(Collectors.groupingBy(f -> f.getIndicator() + "|" + f.getSubject()));

        double k = threshold != null ? threshold : 2.5;
        List<AnomalyCorrectionEntity> corrections = new ArrayList<>();
        List<FactEntity> toUpdate = new ArrayList<>();

        for (Map.Entry<String, List<FactEntity>> entry : groups.entrySet()) {
            List<FactEntity> group = entry.getValue();
            if (group.size() < 5) continue;

            Map<Integer, List<FactEntity>> monthFacts = new HashMap<>();
            for (FactEntity f : group) {
                int month = Integer.parseInt(f.getPeriod().split("-")[1]);
                monthFacts.computeIfAbsent(month, m -> new ArrayList<>()).add(f);
            }

            for (Map.Entry<Integer, List<FactEntity>> mEntry : monthFacts.entrySet()) {
                int month = mEntry.getKey();
                List<FactEntity> fList = mEntry.getValue();

                List<Double> allVals = fList.stream().map(FactEntity::getValue).collect(Collectors.toList());
                Collections.sort(allVals);

                double q1 = percentile(allVals, 0.25);
                double q3 = percentile(allVals, 0.75);
                double iqr = q3 - q1;
                double low = q1 - k * iqr;
                double high = q3 + k * iqr;

                List<Double> cleanVals = new ArrayList<>();
                for (double v : allVals) {
                    if (v >= low && v <= high) {
                        cleanVals.add(v);
                    }
                }

                double cleanMedian;
                if (!cleanVals.isEmpty()) {
                    Collections.sort(cleanVals);
                    cleanMedian = cleanVals.size() % 2 == 0 ?
                            (cleanVals.get(cleanVals.size() / 2 - 1) + cleanVals.get(cleanVals.size() / 2)) / 2.0 :
                            cleanVals.get(cleanVals.size() / 2);
                } else {
                    cleanMedian = allVals.size() % 2 == 0 ?
                            (allVals.get(allVals.size() / 2 - 1) + allVals.get(allVals.size() / 2)) / 2.0 :
                            allVals.get(allVals.size() / 2);
                }

                for (FactEntity f : fList) {
                    if (f.getValue() < low || f.getValue() > high) {
                        AnomalyCorrectionEntity correction = new AnomalyCorrectionEntity();
                        correction.setFactId(f.getId());
                        correction.setOriginalValue(f.getValue());
                        correction.setCleanedValue(cleanMedian);
                        correction.setPeriod(f.getPeriod());
                        correction.setSubject(f.getSubject());
                        correction.setIndicator(f.getIndicator());
                        correction.setCreatedAt(LocalDateTime.now());
                        correction.setUserId(userId);
                        corrections.add(correction);

                        f.setValue(cleanMedian);
                        toUpdate.add(f);
                    }
                }
            }
        }

        if (corrections.isEmpty()) {
            return 0;
        }

        correctionRepository.saveAll(corrections);
        factRepository.saveAll(toUpdate);

        invalidateCache();

        log.info("Заменено {} аномалий для пользователя {}", corrections.size(), userId);
        return corrections.size();
    }

    @Transactional
    public int restoreAnomalies(String userId) {
        if (userId == null) userId = DEFAULT_USER;
        List<AnomalyCorrectionEntity> corrections = correctionRepository.findByUserId(userId);
        if (corrections.isEmpty()) return 0;

        List<FactEntity> toUpdate = new ArrayList<>();
        for (AnomalyCorrectionEntity corr : corrections) {
            factRepository.findById(corr.getFactId()).ifPresent(fact -> {
                fact.setValue(corr.getOriginalValue());
                toUpdate.add(fact);
            });
        }
        factRepository.saveAll(toUpdate);
        correctionRepository.deleteByUserId(userId);

        invalidateCache();

        log.info("Восстановлено {} аномалий для пользователя {}", corrections.size(), userId);
        return corrections.size();
    }

    public boolean hasActiveCorrections(String userId) {
        if (userId == null) userId = DEFAULT_USER;
        return correctionRepository.existsByUserId(userId);
    }

    @Transactional
    public void clearAllCorrections() {
        correctionRepository.deleteAll();
        invalidateCache();
    }

    private double percentile(List<Double> sorted, double p) {
        int n = sorted.size();
        double index = p * (n - 1);
        int low = (int) Math.floor(index);
        int high = (int) Math.ceil(index);
        if (low == high) return sorted.get(low);
        double frac = index - low;
        return sorted.get(low) * (1 - frac) + sorted.get(high) * frac;
    }
}