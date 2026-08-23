package com.dv.dvplanningpoker.config;

import com.dv.dvplanningpoker.websocket.PokerWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PokerWebSocketHandler pokerWebSocketHandler;

    public WebSocketConfig(PokerWebSocketHandler pokerWebSocketHandler) {
        this.pokerWebSocketHandler = pokerWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pokerWebSocketHandler, "/ws")
                .setAllowedOrigins(
                        "https://dv-planning-poker.onrender.com",
                        "http://localhost:8080",
                        "http://127.0.0.1:8080"
                );
    }
}