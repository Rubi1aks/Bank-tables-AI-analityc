package com.example.universityanalytics.service;

import com.example.universityanalytics.entity.FactEntity;
import com.example.universityanalytics.repository.FactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UploadDataService {

    private static final Logger log = LoggerFactory.getLogger(UploadDataService.class);
    private final FactRepository factRepository;
    private final ExcelParserService excelParserService;

    public UploadDataService(FactRepository factRepository, ExcelParserService excelParserService) {
        this.factRepository = factRepository;
        this.excelParserService = excelParserService;
    }

    @Transactional
    public void uploadAndUpsert(MultipartFile file) throws Exception {
        // 1. Парсим
        List<FactEntity> newFacts = excelParserService.parseExcel(file);
        if (newFacts.isEmpty()) {
            throw new IllegalArgumentException("Файл не содержит данных для загрузки");
        }

        // 2. Группируем по (period, subject) для удаления старых
        Map<String, List<FactEntity>> grouped = newFacts.stream()
                .collect(Collectors.groupingBy(f -> f.getPeriod() + "|" + f.getSubject()));

        // 3. Удаляем старые записи для этих комбинаций
        for (String key : grouped.keySet()) {
            String[] parts = key.split("\\|");
            String period = parts[0];
            String subject = parts[1];
            factRepository.deleteByPeriodAndSubject(period, subject);
        }

        // 4. Сохраняем новые данные (batch)
        factRepository.saveAll(newFacts);

        // 5. Добавляем недостающие индикаторы для всех существующих (period, subject)
        addMissingIndicators(newFacts);

        log.info("Загрузка завершена: добавлено {} записей", newFacts.size());
    }

    private void addMissingIndicators(List<FactEntity> newFacts) {
        // Какие индикаторы уже есть в БД
        Set<String> existingIndicators = new HashSet<>(factRepository.findDistinctIndicators());
        // Какие индикаторы пришли в новом файле
        Set<String> newIndicators = newFacts.stream()
                .map(FactEntity::getIndicator)
                .collect(Collectors.toSet());

        // Индикаторы, которых ещё нет в БД
        Set<String> missing = newIndicators.stream()
                .filter(i -> !existingIndicators.contains(i))
                .collect(Collectors.toSet());

        if (missing.isEmpty()) return;

        // Получаем все существующие (period, subject)
        List<String> allPeriods = factRepository.findAllPeriods();
        List<String> allSubjects = factRepository.findAllSubjects();

        List<FactEntity> zeroFacts = new ArrayList<>();
        for (String period : allPeriods) {
            for (String subject : allSubjects) {
                for (String indicator : missing) {
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

        if (!zeroFacts.isEmpty()) {
            factRepository.saveAll(zeroFacts);
            log.info("Добавлено {} записей с нулевыми значениями для новых индикаторов", zeroFacts.size());
        }
    }
}