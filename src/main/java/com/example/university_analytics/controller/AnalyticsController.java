@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final FactRepository factRepository;
    private final UploadDataService uploadDataService;

    // Конструктор...

    // 1. Получить все факты (Long-формат)
    @GetMapping("/facts")
    public ResponseEntity<List<FactEntity>> getFacts(
            @RequestParam(required = false) String subject) {
        if (subject != null && !subject.isEmpty()) {
            return ResponseEntity.ok(factRepository.findBySubject(subject));
        }
        return ResponseEntity.ok(factRepository.findAll());
    }

    // 2. Получить список регионов
    @GetMapping("/regions")
    public ResponseEntity<List<String>> getRegions() {
        return ResponseEntity.ok(factRepository.findDistinctSubjects());
    }

    // 3. Получить список показателей (для фронта)
    @GetMapping("/indicators")
    public ResponseEntity<List<String>> getIndicators() {
        return ResponseEntity.ok(factRepository.findDistinctIndicators());
    }

    // 4. Загрузка файла (НОВЫЙ!)
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String uploadId) {

        if (uploadId == null) {
            uploadId = "up_" + System.currentTimeMillis();
        }

        try {
            uploadDataService.uploadAndUpsert(file);
            Map<String, String> response = new HashMap<>();
            response.put("uploadId", uploadId);
            response.put("message", "Данные успешно загружены и пересчитаны.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // 5. Сценарии — шлём запрос в Python-микросервис (заглушка)
    @GetMapping("/scenarios")
    public ResponseEntity<List<ScenarioResponse>> getScenarios(
            @RequestParam String subject,
            @RequestParam int horizonMonths,
            @RequestParam String method) {

        // TODO: Вызов Python-микросервиса
        // String url = "http://localhost:5000/predict?subject=" + subject + "&horizon=" + horizonMonths;
        // ResponseEntity<PythonResponse> response = restTemplate.getForEntity(url, PythonResponse.class);
        // return convertAndRespond(response);

        // Пока заглушка
        return ResponseEntity.ok(generateMockScenarios(subject));
    }
}