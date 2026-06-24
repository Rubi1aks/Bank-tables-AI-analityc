package com.example.universityanalytics.service;

import com.example.universityanalytics.dto.ProgressMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProgressService {

    private final SimpMessagingTemplate messagingTemplate;

    // Конструктор
    public ProgressService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Отправить сообщение о прогрессе по WebSocket
     * @param uploadId ID загрузки
     * @param phase Этап обработки
     * @param percent Процент (0-100)
     * @param message Текст сообщения
     */
    public void sendProgress(String uploadId, String phase, Integer percent, String message) {
        ProgressMessage progress = new ProgressMessage(phase, percent, message, null);
        messagingTemplate.convertAndSend("/topic/upload/" + uploadId, progress);
        System.out.printf("[Progress] %s: %d%% - %s%n", phase, percent, message);
    }

    /**
     * Отправить финальное сообщение с uploadId
     */
    public void sendCompleted(String uploadId, String message) {
        ProgressMessage progress = new ProgressMessage("COMPLETED", 100, message, uploadId);
        messagingTemplate.convertAndSend("/topic/upload/" + uploadId, progress);
        System.out.printf("[Progress] COMPLETED: %s%n", message);
    }
}