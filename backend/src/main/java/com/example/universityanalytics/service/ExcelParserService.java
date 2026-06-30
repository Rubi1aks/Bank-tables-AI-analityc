package com.example.universityanalytics.service;

import com.example.universityanalytics.entity.FactEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelParserService {

    public List<FactEntity> parseExcel(MultipartFile file) throws Exception {
        // ВРЕМЕННАЯ ЗАГЛУШКА
        List<FactEntity> facts = new ArrayList<>();
        facts.add(new FactEntity("2025-01", "Центральный", "Москва", "Доход банка", "руб", 100000.0));
        facts.add(new FactEntity("2025-01", "Центральный", "Москва", "Студенты", "чел", 5000.0));
        facts.add(new FactEntity("2025-02", "Центральный", "Москва", "Доход банка", "руб", 120000.0));
        facts.add(new FactEntity("2025-02", "Центральный", "Москва", "Студенты", "чел", 5200.0));
        return facts;
    }
}