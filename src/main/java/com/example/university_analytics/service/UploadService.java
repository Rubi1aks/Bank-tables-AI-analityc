package com.example.universityanalytics.service;

import com.example.universityanalytics.entity.FactEntity;
import com.example.universityanalytics.repository.FactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UploadDataService {

    private final FactRepository factRepository;
    private final ExcelParserService excelParserService;

    public UploadDataService(FactRepository factRepository, ExcelParserService excelParserService) {
        this.factRepository = factRepository;
        this.excelParserService = excelParserService;
    }

    @Transactional
    public void uploadAndUpsert(MultipartFile file) throws Exception {
        List<FactEntity> newFacts = excelParserService.parseExcel(file);

        // 1. Группируем по (period, subject)
        var groupByKey = newFacts.stream()
                .collect(Collectors.groupingBy(f -> f.getPeriod() + "|" + f.getSubject()));

        // 2. Для каждой группы: удаляем старые, вставляем новые
        for (var entry : groupByKey.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            String period = parts[0];
            String subject = parts[1];
            List<FactEntity> factsForThisCell = entry.getValue();

            // Удаляем старые данные за этот период и регион
            factRepository.deleteByPeriodAndSubject(period, subject);

            // Вставляем новые
            factRepository.saveAll(factsForThisCell);
        }

        // 3. Добавляем нулевые значения для новых показателей
        addMissingIndicators(newFacts);
    }

    private void addMissingIndicators(List<FactEntity> newFacts) {
        Set<String> existingIndicators = Set.copyOf(factRepository.findDistinctIndicators());
        Set<String> newIndicators = newFacts.stream()
                .map(FactEntity::getIndicator)
                .collect(Collectors.toSet());

        Set<String> missingIndicators = newIndicators.stream()
                .filter(i -> !existingIndicators.contains(i))
                .collect(Collectors.toSet());

        if (missingIndicators.isEmpty()) return;

        // Для всех существующих (period, subject) добавляем missingIndicators со значением 0
        List<String> allPeriods = factRepository.findAll().stream()
                .map(FactEntity::getPeriod).distinct().toList();
        List<String> allSubjects = factRepository.findAll().stream()
                .map(FactEntity::getSubject).distinct().toList();

        List<FactEntity> zeroFacts = new ArrayList<>();
        for (String period : allPeriods) {
            for (String subject : allSubjects) {
                for (String indicator : missingIndicators) {
                    FactEntity zero = new FactEntity();
                    zero.setPeriod(period);
                    zero.setSubject(subject);
                    zero.setIndicator(indicator);
                    zero.setUnit("чел"); // или дефолтный
                    zero.setValue(0.0);
                    zeroFacts.add(zero);
                }
            }
        }
        factRepository.saveAll(zeroFacts);
    }
}