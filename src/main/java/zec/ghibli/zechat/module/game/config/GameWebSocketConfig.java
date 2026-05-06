package zec.ghibli.zechat.module.game.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import zec.ghibli.zechat.module.game.handler.GomokuWebSocketHandler;

@Configuration
@EnableWebSocket
public class GameWebSocketConfig implements WebSocketConfigurer {

    private final GomokuWebSocketHandler handler;

    public GameWebSocketConfig(GomokuWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/gomoku").setAllowedOrigins("*");
    }
}
