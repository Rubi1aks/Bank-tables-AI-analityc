package com.example.universityanalytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Эндпоинт для WebSocket подключения
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")  // разрешаем CORS
                .withSockJS();            // fallback для браузеров без WebSocket
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Префикс для отправки сообщений от сервера к клиенту
        registry.enableSimpleBroker("/topic");

        // Префикс для сообщений от клиента к серверу
        registry.setApplicationDestinationPrefixes("/app");
    }
}