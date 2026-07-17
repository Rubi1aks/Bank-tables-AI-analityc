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
        List<FactEntity> newFacts = excelParserService.parseExcel(file);
        if (newFacts.isEmpty()) {
            throw new IllegalArgumentException("Файл не содержит данных для загрузки");
        }

        log.info("Загружено {} записей из файла", newFacts.size());

        List<FactEntity> existingFacts = factRepository.findAll();

        Map<String, FactEntity> existingMap = new HashMap<>();
        for (FactEntity f : existingFacts) {
            String key = f.getPeriod() + "|" + f.getSubject() + "|" + f.getIndicator();
            existingMap.put(key, f);
        }

        Map<String, FactEntity> newMap = new HashMap<>();
        for (FactEntity f : newFacts) {
            String key = f.getPeriod() + "|" + f.getSubject() + "|" + f.getIndicator();
            newMap.put(key, f);
        }

        List<FactEntity> toSave = new ArrayList<>();

        for (Map.Entry<String, FactEntity> entry : newMap.entrySet()) {
            String key = entry.getKey();
            FactEntity newFact = entry.getValue();
            FactEntity existing = existingMap.get(key);

            if (existing != null) {
                existing.setValue(newFact.getValue());
                if (newFact.getUnit() != null && !newFact.getUnit().isEmpty()) {
                    existing.setUnit(newFact.getUnit());
                }
                if (newFact.getDistrict() != null && !newFact.getDistrict().isEmpty()) {
                    existing.setDistrict(newFact.getDistrict());
                }
                toSave.add(existing);
                existingMap.remove(key);
            } else {
                toSave.add(newFact);
            }
        }

        log.info("Обновлено/добавлено {} записей, {} существующих записей сохранены без изменений",
                toSave.size(), existingMap.size());
        factRepository.saveAll(toSave);

        log.info("Загрузка завершена. Всего в БД записей: {}", factRepository.count());
    }
}