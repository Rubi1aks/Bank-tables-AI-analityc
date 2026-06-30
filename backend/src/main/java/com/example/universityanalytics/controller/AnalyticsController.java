package com.example.universityanalytics.controller;

import com.example.universityanalytics.entity.FactEntity;
import com.example.universityanalytics.repository.FactRepository;
import com.example.universityanalytics.service.UploadDataService;
import com.example.universityanalytics.dto.ScenarioResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final FactRepository factRepository;
    private final UploadDataService uploadDataService;

    public AnalyticsController(FactRepository factRepository, UploadDataService uploadDataService) {
        this.factRepository = factRepository;
        this.uploadDataService = uploadDataService;
    }

    @GetMapping("/facts")
    public ResponseEntity<List<FactEntity>> getFacts(@RequestParam(required = false) String subject) {
        if (subject != null && !subject.isEmpty()) {
            return ResponseEntity.ok(factRepository.findBySubject(subject));
        }
        return ResponseEntity.ok(factRepository.findAll());
    }

    @GetMapping("/regions")
    public ResponseEntity<List<String>> getRegions() {
        return ResponseEntity.ok(factRepository.findDistinctSubjects());
    }

    @GetMapping("/indicators")
    public ResponseEntity<List<String>> getIndicators() {
        return ResponseEntity.ok(factRepository.findDistinctIndicators());
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            uploadDataService.uploadAndUpsert(file);
            Map<String, String> response = new HashMap<>();
            response.put("status", "ok");
            response.put("message", "Данные успешно загружены");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<ScenarioResponse>> getScenarios(
            @RequestParam String subject,
            @RequestParam int horizonMonths,
            @RequestParam String method) {
        // ЗАГЛУШКА
        List<ScenarioResponse> scenarios = new ArrayList<>();
        List<ScenarioResponse.ScenarioPoint> points = new ArrayList<>();
        points.add(new ScenarioResponse.ScenarioPoint("2025-07", 31000.0, 28000.0, 34000.0));
        points.add(new ScenarioResponse.ScenarioPoint("2025-08", 32000.0, 29000.0, 35000.0));
        scenarios.add(new ScenarioResponse("BASELINE", "Базовый сценарий", points, 0.12));
        return ResponseEntity.ok(scenarios);
    }
}