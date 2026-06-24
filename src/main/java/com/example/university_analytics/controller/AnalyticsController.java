package com.example.universityanalytics.controller;

import com.example.universityanalytics.dto.FactResponse;
import com.example.universityanalytics.dto.NewsResponse;
import com.example.universityanalytics.dto.ScenarioResponse;
import com.example.universityanalytics.entity.BankAnalyticsEntity;
import com.example.universityanalytics.repository.BankAnalyticsRepository;
import com.example.universityanalytics.service.AnalyticsService;
import com.example.universityanalytics.service.ScenarioService;
import com.example.universityanalytics.service.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final ScenarioService scenarioService;
    private final BankAnalyticsRepository repository;
    private final UploadService uploadService;

    // Конструктор
    public AnalyticsController(AnalyticsService analyticsService,
                               ScenarioService scenarioService,
                               BankAnalyticsRepository repository,
                               UploadService uploadService) {
        this.analyticsService = analyticsService;
        this.scenarioService = scenarioService;
        this.repository = repository;
        this.uploadService = uploadService;
    }

    // 1. Получение исторических данных (Wide Table)
    @GetMapping("/facts")
    public ResponseEntity<List<FactResponse>> getFacts(
            @RequestParam(required = false) String subject) {
        log.info("Запрос фактов для региона: {}", subject != null ? subject : "все регионы");
        return ResponseEntity.ok(analyticsService.getFacts(subject));
    }

    // 2. Получение сценариев прогноза
    @GetMapping("/scenarios")
    public ResponseEntity<List<ScenarioResponse>> getScenarios(
            @RequestParam String subject) {
        log.info("Запрос сценариев для региона: {}", subject);
        return ResponseEntity.ok(scenarioService.generateScenarios(subject));
    }

    // 3. Список регионов
    @GetMapping("/regions")
    public ResponseEntity<List<String>> getRegions() {
        log.info("Запрос списка регионов");
        List<String> regions = repository.findAll().stream()
                .map(BankAnalyticsEntity::getSubject)
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(regions);
    }

    // 4. Загрузка файла
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file) {
        log.info("Загрузка файла: {}", file.getOriginalFilename());
        String uploadId = "up_" + System.currentTimeMillis();
        uploadService.processFileAsync(file, uploadId);
        Map<String, String> response = new HashMap<>();
        response.put("uploadId", uploadId);
        response.put("message", "Файл принят в обработку. Следите за прогрессом по WebSocket.");
        return ResponseEntity.ok(response);
    }

    // 5. Новости
    @GetMapping("/news")
    public ResponseEntity<List<NewsResponse>> getNews(
            @RequestParam String subject) {
        log.info("Запрос новостей для региона: {}", subject);
        return ResponseEntity.ok(analyticsService.getNews(subject));
    }

    // 6. Аномалии (заглушка)
    @GetMapping("/anomalies")
    public ResponseEntity<List<Map<String, Object>>> getAnomalies(
            @RequestParam String subject) {
        log.info("Запрос аномалий для региона: {}", subject);
        List<Map<String, Object>> anomalies = List.of(
                Map.of(
                        "period", "2025-07",
                        "indicator", "transactionVolume",
                        "expected", 623152.0,
                        "actual", 480000.0,
                        "deviation", -23.0,
                        "description", "Сезонное снижение активности в летний период"
                )
        );
        return ResponseEntity.ok(anomalies);
    }

    // 7. Экспорт в CSV с BOM
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportToCsv(@RequestParam(required = false) String subject) throws IOException {
        log.info("Экспорт в CSV для региона: {}", subject != null ? subject : "все регионы");

        List<BankAnalyticsEntity> data;
        if (subject != null && !subject.isEmpty()) {
            data = repository.findBySubject(subject);
        } else {
            data = repository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // BOM
        csv.append("Период;Федеральный округ;Субъект РФ;Доход банка;Тариф банка %;Объем транзакций;Средняя стоимость обеда;Количество клиентов;Всего людей в ВУЗе;Доля питающихся %;Студенты;Административный персонал\n");

        for (BankAnalyticsEntity e : data) {
            csv.append(String.format("%s;%s;%s;%.2f;%.2f;%.2f;%.2f;%d;%d;%.2f;%d;%d\n",
                    e.getPeriod(),
                    e.getDistrict(),
                    e.getSubject(),
                    e.getBankIncome(),
                    e.getAvgBankTariffPct(),
                    e.getTransactionVolume(),
                    e.getAvgLunchCost(),
                    e.getClientCount(),
                    e.getTotalUniversityPeople(),
                    e.getCanteenEatersPct(),
                    e.getStudents(),
                    e.getAdminStaff()
            ));
        }

        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "analytics_export.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    // 8. Граф зависимостей
    @GetMapping("/graph")
    public ResponseEntity<Map<String, Object>> getGraph(@RequestParam String subject) {
        List<BankAnalyticsEntity> data = repository.findBySubject(subject);
        if (data.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BankAnalyticsEntity last = data.get(data.size() - 1);

        Map<String, Object> graph = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // Узлы
        Map<String, Object> node1 = new HashMap<>();
        node1.put("id", "students");
        node1.put("label", "Студенты");
        node1.put("value", last.getStudents());
        node1.put("unit", "чел");
        nodes.add(node1);

        Map<String, Object> node2 = new HashMap<>();
        node2.put("id", "adminStaff");
        node2.put("label", "Административный персонал");
        node2.put("value", last.getAdminStaff());
        node2.put("unit", "чел");
        nodes.add(node2);

        Map<String, Object> node3 = new HashMap<>();
        node3.put("id", "totalPeople");
        node3.put("label", "Общее количество людей");
        node3.put("value", last.getTotalUniversityPeople());
        node3.put("unit", "чел");
        nodes.add(node3);

        Map<String, Object> node4 = new HashMap<>();
        node4.put("id", "canteenEaters");
        node4.put("label", "Доля питающихся в столовой");
        node4.put("value", last.getCanteenEatersPct());
        node4.put("unit", "%");
        nodes.add(node4);

        Map<String, Object> node5 = new HashMap<>();
        node5.put("id", "clients");
        node5.put("label", "Клиенты банка");
        node5.put("value", last.getClientCount());
        node5.put("unit", "чел");
        nodes.add(node5);

        Map<String, Object> node6 = new HashMap<>();
        node6.put("id", "lunchCost");
        node6.put("label", "Средняя стоимость обеда");
        node6.put("value", last.getAvgLunchCost());
        node6.put("unit", "руб");
        nodes.add(node6);

        Map<String, Object> node7 = new HashMap<>();
        node7.put("id", "transactionVolume");
        node7.put("label", "Объем транзакций");
        node7.put("value", last.getTransactionVolume());
        node7.put("unit", "руб");
        nodes.add(node7);

        Map<String, Object> node8 = new HashMap<>();
        node8.put("id", "tariff");
        node8.put("label", "Тариф банка");
        node8.put("value", last.getAvgBankTariffPct());
        node8.put("unit", "%");
        nodes.add(node8);

        Map<String, Object> node9 = new HashMap<>();
        node9.put("id", "income");
        node9.put("label", "Доход банка");
        node9.put("value", last.getBankIncome());
        node9.put("unit", "руб");
        node9.put("isTarget", true);
        nodes.add(node9);

        // Связи
        edges.add(Map.of("from", "students", "to", "totalPeople", "label", "+"));
        edges.add(Map.of("from", "adminStaff", "to", "totalPeople", "label", "+"));
        edges.add(Map.of("from", "totalPeople", "to", "clients", "label", "×"));
        edges.add(Map.of("from", "canteenEaters", "to", "clients", "label", "×"));
        edges.add(Map.of("from", "clients", "to", "transactionVolume", "label", "×"));
        edges.add(Map.of("from", "lunchCost", "to", "transactionVolume", "label", "×"));
        edges.add(Map.of("from", "transactionVolume", "to", "income", "label", "×"));
        edges.add(Map.of("from", "tariff", "to", "income", "label", "×"));

        graph.put("subject", subject);
        graph.put("period", last.getPeriod());
        graph.put("nodes", nodes);
        graph.put("edges", edges);

        return ResponseEntity.ok(graph);
    }

    // 9. Сезонность
    @GetMapping("/seasonality")
    public ResponseEntity<Map<String, Double>> getSeasonality(@RequestParam String subject) {
        List<BankAnalyticsEntity> data = repository.findBySubject(subject);
        if (data.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<Integer, List<Double>> monthlyIncomes = new HashMap<>();
        for (BankAnalyticsEntity e : data) {
            String[] parts = e.getPeriod().split("-");
            int month = Integer.parseInt(parts[1]);
            monthlyIncomes.computeIfAbsent(month, k -> new ArrayList<>()).add(e.getBankIncome());
        }

        Map<Integer, Double> monthlyAvg = new HashMap<>();
        double totalAvg = 0;
        for (Map.Entry<Integer, List<Double>> entry : monthlyIncomes.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            monthlyAvg.put(entry.getKey(), avg);
            totalAvg += avg;
        }
        totalAvg /= 12;

        Map<String, Double> seasonality = new LinkedHashMap<>();
        String[] monthNames = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};

        for (int i = 1; i <= 12; i++) {
            double avg = monthlyAvg.getOrDefault(i, 0.0);
            double index = totalAvg > 0 ? avg / totalAvg : 1.0;
            seasonality.put(monthNames[i-1], Math.round(index * 100.0) / 100.0);
        }

        return ResponseEntity.ok(seasonality);
    }
}