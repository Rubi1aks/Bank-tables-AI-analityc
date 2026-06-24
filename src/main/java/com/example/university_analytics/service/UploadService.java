package com.example.universityanalytics.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    private final ProgressService progressService;

    // Конструктор
    public UploadService(ProgressService progressService) {
        this.progressService = progressService;
    }

    @Async
    public void processFileAsync(MultipartFile file, String uploadId) {
        try {
            progressService.sendProgress(uploadId, "VALIDATING", 0, "Проверка файла...");
            Thread.sleep(1000);

            progressService.sendProgress(uploadId, "PARSING", 20, "Чтение данных из файла...");
            Thread.sleep(1500);

            progressService.sendProgress(uploadId, "PARSING", 40, "Обработка строк: 500 из 1000");
            Thread.sleep(1000);

            progressService.sendProgress(uploadId, "SAVING", 55, "Сохранение данных в БД...");
            Thread.sleep(1500);

            progressService.sendProgress(uploadId, "SAVING", 70, "Сохранение завершено");
            Thread.sleep(500);

            progressService.sendProgress(uploadId, "REFRESHING_VIEWS", 80, "Обновление аналитических представлений...");
            Thread.sleep(2000);

            progressService.sendProgress(uploadId, "REFRESHING_VIEWS", 95, "Представления обновлены");
            Thread.sleep(500);

            progressService.sendCompleted(uploadId, "Загрузка завершена! Данные готовы для анализа.");

        } catch (InterruptedException e) {
            progressService.sendProgress(uploadId, "ERROR", 0, "Ошибка: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            progressService.sendProgress(uploadId, "ERROR", 0, "Ошибка: " + e.getMessage());
        }
    }
}