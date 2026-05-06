package zec.ghibli.zechat.module.game.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import zec.ghibli.zechat.module.game.service.GameRoomService;

@Component
public class GomokuWebSocketHandler extends TextWebSocketHandler {

    private final GameRoomService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public GomokuWebSocketHandler(GameRoomService gameRoomService) {
        service = gameRoomService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String type = node.get("type").asText();
        switch (type) {
            case "join" -> service.join(session, node.get("username").asText(), node.get("roomId").asText());
            case "move" -> service.move(session, node.get("x").asInt(), node.get("y").asInt());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        service.disconnect(session);
    }
}
