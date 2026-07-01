package com.example.universityanalytics.service;

import com.example.universityanalytics.entity.FactEntity;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class ExcelParserService {

    private static final Logger log = LoggerFactory.getLogger(ExcelParserService.class);

    public List<FactEntity> parseExcel(MultipartFile file) throws Exception {
        List<FactEntity> facts = new ArrayList<>();
        Map<Integer, String> indicatorMap = new LinkedHashMap<>();
        int periodIdx = -1, districtIdx = -1, subjectIdx = -1;

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new IllegalArgumentException("Файл пуст или нет заголовков");

            // Определяем колонки
            for (Cell cell : headerRow) {
                String header = cell.getStringCellValue().trim();
                switch (header.toLowerCase()) {
                    case "отчетный период":
                    case "период":
                        periodIdx = cell.getColumnIndex();
                        break;
                    case "федеральный округ рф":
                    case "округ":
                        districtIdx = cell.getColumnIndex();
                        break;
                    case "субъект рф":
                    case "регион":
                        subjectIdx = cell.getColumnIndex();
                        break;
                    default:
                        if (!header.isEmpty()) {
                            indicatorMap.put(cell.getColumnIndex(), header);
                        }
                }
            }

            if (periodIdx == -1 || subjectIdx == -1) {
                throw new IllegalArgumentException("Не найдены обязательные колонки: 'Период' и 'Субъект РФ'");
            }

            // Проход по строкам данных
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String period = getCellString(row, periodIdx);
                String district = districtIdx != -1 ? getCellString(row, districtIdx) : "";
                String subject = getCellString(row, subjectIdx);

                if (period.isEmpty() || subject.isEmpty()) {
                    log.warn("Пропущена строка {}: пустой период или субъект", row.getRowNum());
                    continue;
                }

                for (Map.Entry<Integer, String> entry : indicatorMap.entrySet()) {
                    int colIdx = entry.getKey();
                    String indicator = entry.getValue();
                    Double value = getCellNumeric(row, colIdx);
                    if (value == null) {
                        log.warn("Значение для {} в строке {} не число, пропускаем", indicator, row.getRowNum());
                        continue;
                    }
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
        } catch (Exception e) {
            log.error("Ошибка парсинга Excel", e);
            throw new Exception("Не удалось распарсить файл: " + e.getMessage(), e);
        }
        return facts;
    }

    private String getCellString(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            default -> "";
        };
    }

    private Double getCellNumeric(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().replace(",", "."));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private String detectUnit(String indicator) {
        String low = indicator.toLowerCase();
        if (low.contains("руб") || low.contains("доход") || low.contains("объем") || low.contains("стоимость")) {
            return "руб";
        } else if (low.contains("%") || low.contains("доля")) {
            return "%";
        } else {
            return "чел";
        }
    }
}