package com.streamlake.config;

import com.streamlake.controller.RealtimeWSHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWSHandler realtimeWSHandler;

    public WebSocketConfig(RealtimeWSHandler realtimeWSHandler) {
        this.realtimeWSHandler = realtimeWSHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeWSHandler, "/ws/realtime")
                .setAllowedOriginPatterns("*");
    }
}
