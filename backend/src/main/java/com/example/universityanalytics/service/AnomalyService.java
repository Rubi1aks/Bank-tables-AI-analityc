package com.example.universityanalytics.service;

import com.example.universityanalytics.dto.AnomalyDto;
import com.example.universityanalytics.entity.FactEntity;
import com.example.universityanalytics.repository.FactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AnomalyService {

    private final FactRepository factRepository;

    public List<AnomalyDto> detectAnomalies(String subject, String indicator, Double threshold) {
        List<FactEntity> facts = (subject != null && !subject.isEmpty())
                ? factRepository.findBySubject(subject)
                : factRepository.findAll();

        if (indicator != null && !indicator.isEmpty()) {
            facts = facts.stream().filter(f -> f.getIndicator().equals(indicator)).toList();
        }

        Map<String, List<FactEntity>> groups = new HashMap<>();
        for (FactEntity f : facts) {
            String key = f.getIndicator() + "|" + f.getSubject();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }

        double t = threshold != null ? threshold : 2.0;
        List<AnomalyDto> anomalies = new ArrayList<>();

        for (Map.Entry<String, List<FactEntity>> entry : groups.entrySet()) {
            List<FactEntity> group = entry.getValue();
            if (group.size() < 5) continue;
            double mean = group.stream().mapToDouble(FactEntity::getValue).average().orElse(0);
            double std = Math.sqrt(group.stream().mapToDouble(v -> Math.pow(v.getValue() - mean, 2)).average().orElse(0));
            for (FactEntity f : group) {
                if (Math.abs(f.getValue() - mean) > t * std) {
                    AnomalyDto dto = new AnomalyDto();
                    dto.setId(f.getSubject() + "-" + f.getIndicator() + "-" + f.getPeriod());
                    dto.setPeriod(f.getPeriod());
                    dto.setIndicator(f.getIndicator());
                    dto.setSubject(f.getSubject());
                    dto.setDeviationPct(((f.getValue() - mean) / mean * 100));
                    dto.setDirection(f.getValue() > mean ? "up" : "down");
                    dto.setText(String.format("%s: %s в %s %s на %.1f%% от среднего",
                            f.getPeriod(), f.getIndicator(), f.getSubject(),
                            f.getValue() > mean ? "вырос" : "снизился",
                            Math.abs((f.getValue() - mean) / mean * 100)));
                    anomalies.add(dto);
                }
            }
        }
        return anomalies;
    }
}