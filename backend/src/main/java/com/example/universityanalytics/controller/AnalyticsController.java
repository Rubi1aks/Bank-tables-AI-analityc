package com.example.universityanalytics.controller;

import com.example.universityanalytics.dto.ScenarioParams;
import com.example.universityanalytics.dto.ScenarioResponse;
import com.example.universityanalytics.entity.FactEntity;
import com.example.universityanalytics.repository.FactRepository;
import com.example.universityanalytics.service.DriversService;
import com.example.universityanalytics.service.ScenarioService;
import com.example.universityanalytics.service.UploadDataService;
import com.fasterxml.jackson.databind.JsonNode;
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

    private final FactRepository factRepository;
    private final UploadDataService uploadDataService;
    private final DriversService driversService;
    private final ScenarioService scenarioService;

    // Можно добавить PythonClientService, если понадобится для /news и /anomalies
    // private final PythonClientService pythonClientService;

    public AnalyticsController(FactRepository factRepository,
                               UploadDataService uploadDataService,
                               DriversService driversService,
                               ScenarioService scenarioService) {
        this.factRepository = factRepository;
        this.uploadDataService = uploadDataService;
        this.driversService = driversService;
        this.scenarioService = scenarioService;
    }

    // ============================================================
    // 1. Факты (исторические данные)
    // ============================================================
    @GetMapping("/facts")
    public ResponseEntity<List<FactEntity>> getFacts(@RequestParam(required = false) String subject) {
        if (subject != null && !subject.isEmpty()) {
            return ResponseEntity.ok(factRepository.findBySubject(subject));
        }
        return ResponseEntity.ok(factRepository.findAll());
    }

    // ============================================================
    // 2. Справочники
    // ============================================================
    @GetMapping("/regions")
    public ResponseEntity<List<String>> getRegions() {
        return ResponseEntity.ok(factRepository.findDistinctSubjects());
    }

    @GetMapping("/indicators")
    public ResponseEntity<List<String>> getIndicators() {
        return ResponseEntity.ok(factRepository.findDistinctIndicators());
    }

    // ============================================================
    // 3. Загрузка файла (с прогрессом через WebSocket)
    // ============================================================
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            uploadDataService.uploadAndUpsert(file);
            Map<String, String> response = new HashMap<>();
            response.put("status", "ok");
            response.put("message", "Данные успешно загружены и пересчитаны.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при загрузке файла", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // ============================================================
    // 4. Драйверы для графика (вкладка "Текущие данные")
    // ============================================================
    @GetMapping("/analytics/drivers")
    public ResponseEntity<List<DriversService.DriverRow>> getDrivers(@RequestParam String subject) {
        return ResponseEntity.ok(driversService.getDrivers(subject));
    }

    // ============================================================
    // 5. Сценарии (4 стандартных + кастомный)
    // ============================================================
    @GetMapping("/scenarios")
    public ResponseEntity<List<ScenarioResponse>> getScenarios(
            @RequestParam String subject,
            @RequestParam(defaultValue = "6") int horizonMonths) {
        return ResponseEntity.ok(scenarioService.getDefaultScenarios(subject, horizonMonths));
    }

    @PostMapping("/scenarios/compute")
    public ResponseEntity<ScenarioResponse> computeScenario(@RequestBody ScenarioParams params) {
        ScenarioResponse response = scenarioService.computeCustomScenario(
                params.getTargetSubject(),
                params.getHorizonMonths(),
                params.getMethod(),
                params.getDriverMultipliers()
        );
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // 6. AI-аналитика (новости, аномалии, резюме)
    // ============================================================
    // Эти методы можно оставить как заглушки или вызывать PythonClientService

    @GetMapping("/news")
    public ResponseEntity<List<Map<String, Object>>> getNews(@RequestParam String subject) {
        // Заглушка — вернуть фиктивные новости
        List<Map<String, Object>> news = List.of(
                Map.of(
                        "id", "1",
                        "title", "Старт учебного года: вузы вернулись к очному формату",
                        "source", "РБК",
                        "date", "2025-09",
                        "summary", "Начало семестра повышает посещаемость столовых — рост числа клиентов.",
                        "impact", "positive"
                ),
                Map.of(
                        "id", "2",
                        "title", "Летние каникулы снизили трафик общепита у вузов",
                        "source", "Коммерсантъ",
                        "date", "2025-07",
                        "summary", "В июле-августе доля питающихся в студенческих столовых падает.",
                        "impact", "negative"
                )
        );
        return ResponseEntity.ok(news);
    }

    @GetMapping("/anomalies")
    public ResponseEntity<List<Map<String, Object>>> getAnomalies(@RequestParam String subject) {
        // Заглушка
        List<Map<String, Object>> anomalies = List.of(
                Map.of(
                        "id", "an-1",
                        "indicator", "Доход банка",
                        "period", "2025-07",
                        "subject", subject,
                        "deviationPct", -22.4,
                        "direction", "down",
                        "text", "Июль 2025: «Доход банка» в регионе " + subject + " снизился на -22.4%."
                )
        );
        return ResponseEntity.ok(anomalies);
    }

    @GetMapping("/ai/summary")
    public ResponseEntity<Map<String, String>> getAiSummary(@RequestParam String subject) {
        // Заглушка — можно заменить на реальный вызов PythonClientService
        Map<String, String> summary = new HashMap<>();
        summary.put("marketNews", "В Северо-Западном ФО зафиксирован рост цен на продукты на 4.2%.");
        summary.put("aiPlanSummary", "Базовый сценарий показывает рост дохода банка YoY на 5.3%.");
        return ResponseEntity.ok(summary);
    }

    // ============================================================
    // 7. Экспорт в CSV (с BOM для русских букв)
    // ============================================================
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportToCsv(@RequestParam(required = false) String subject) throws IOException {
        log.info("Экспорт в CSV для региона: {}", subject != null ? subject : "все регионы");

        List<FactEntity> data;
        if (subject != null && !subject.isEmpty()) {
            data = factRepository.findBySubject(subject);
        } else {
            data = factRepository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // BOM
        csv.append("Период;Округ;Субъект;Показатель;Ед.изм.;Значение\n");

        for (FactEntity e : data) {
            csv.append(String.format("%s;%s;%s;%s;%s;%.2f\n",
                    e.getPeriod(),
                    e.getDistrict(),
                    e.getSubject(),
                    e.getIndicator(),
                    e.getUnit(),
                    e.getValue()
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
}