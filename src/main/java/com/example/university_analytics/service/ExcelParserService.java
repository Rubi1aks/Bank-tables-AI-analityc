package com.example.universityanalytics.service;

import com.example.universityanalytics.entity.FactEntity;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class ExcelParserService {

    public List<FactEntity> parseExcel(MultipartFile file) throws Exception {
        List<FactEntity> facts = new ArrayList<>();
        List<String> indicators = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            // 1. Определяем индексы колонок
            int periodIdx = -1, districtIdx = -1, subjectIdx = -1;
            Map<Integer, String> indicatorMap = new HashMap<>();

            for (Cell cell : headerRow) {
                String header = cell.getStringCellValue().trim();
                switch (header) {
                    case "Отчетный период":
                    case "Период":
                        periodIdx = cell.getColumnIndex();
                        break;
                    case "Федеральный округ РФ":
                    case "Округ":
                        districtIdx = cell.getColumnIndex();
                        break;
                    case "Субъект РФ":
                    case "Регион":
                        subjectIdx = cell.getColumnIndex();
                        break;
                    default:
                        // Всё остальное — это показатели!
                        indicatorMap.put(cell.getColumnIndex(), header);
                        indicators.add(header);
                }
            }

            // 2. Проходим по строкам данных
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // пропускаем заголовок

                String period = getCellValue(row, periodIdx);
                String district = getCellValue(row, districtIdx);
                String subject = getCellValue(row, subjectIdx);

                if (period.isEmpty() || subject.isEmpty()) continue;

                for (Map.Entry<Integer, String> entry : indicatorMap.entrySet()) {
                    int colIdx = entry.getKey();
                    String indicator = entry.getValue();
                    double value = getNumericValue(row, colIdx);

                    FactEntity fact = new FactEntity();
                    fact.setPeriod(period);
                    fact.setDistrict(district);
                    fact.setSubject(subject);
                    fact.setIndicator(indicator);
                    fact.setUnit(detectUnit(indicator));
                    fact.setValue(value);

                    facts.add(fact);
                }
            }
        }
        return facts;
    }

    private String getCellValue(Row row, int index) {
        if (index < 0) return "";
        Cell cell = row.getCell(index);
        return cell != null ? cell.getStringCellValue().trim() : "";
    }

    private double getNumericValue(Row row, int index) {
        if (index < 0) return 0.0;
        Cell cell = row.getCell(index);
        if (cell == null) return 0.0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().replace(",", "."));
                } catch (NumberFormatException e) {
                    yield 0.0;
                }
            }
            default -> 0.0;
        };
    }

    private String detectUnit(String indicator) {
        if (indicator.toLowerCase().contains("руб") || indicator.contains("доход") || indicator.contains("объем")) {
            return "руб";
        } else if (indicator.toLowerCase().contains("%") || indicator.toLowerCase().contains("доля")) {
            return "%";
        } else {
            return "чел";
        }
    }
}