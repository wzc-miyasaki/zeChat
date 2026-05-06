package zec.ghibli.zechat.module.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import zec.ghibli.zechat.module.game.model.GameRoom;
import zec.ghibli.zechat.module.game.model.UserSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameRoomService {

    private final UserService userService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private volatile GameRoom waitingRoom = null;

    public GameRoomService(UserService userService) {
        this.userService = userService;
    }

    public synchronized void join(WebSocketSession session, String token) throws IOException {
        UserSession user = userService.validate(token);
        if (user == null) { send(session, Map.of("type", "error", "message", "未登录")); return; }

        if (waitingRoom == null) {
            GameRoom room = new GameRoom();
            room.sessions[0] = session;
            room.playerIds[0] = user.username();
            rooms.put(room.roomId, room);
            waitingRoom = room;
            send(session, Map.of("type", "waiting", "roomId", room.roomId));
        } else {
            GameRoom room = waitingRoom;
            waitingRoom = null;
            room.sessions[1] = session;
            room.playerIds[1] = user.username();
            send(room.sessions[0], Map.of("type", "start", "color", "black", "roomId", room.roomId));
            send(room.sessions[1], Map.of("type", "start", "color", "white", "roomId", room.roomId));
        }
    }

    public synchronized void rejoin(WebSocketSession session, String roomId, String token) throws IOException {
        UserSession user = userService.validate(token);
        if (user == null) { send(session, Map.of("type", "error", "message", "未登录")); return; }

        GameRoom room = rooms.get(roomId);
        if (room == null || room.finished) {
            send(session, Map.of("type", "error", "message", "room not found")); return;
        }
        // Find the slot that belongs to this userId
        int slot = -1;
        for (int i = 0; i < 2; i++) {
            if (user.username().equals(room.playerIds[i])) { slot = i; break; }
        }
        if (slot == -1) { send(session, Map.of("type", "error", "message", "room not found")); return; }
        if (room.sessions[slot] != null && room.sessions[slot].isOpen()) {
            send(session, Map.of("type", "error", "message", "already connected")); return;
        }

        room.sessions[slot] = session;
        room.disconnectedAt = -1;
        String color = slot == 0 ? "black" : "white";
        String turn  = room.turn == 0 ? "black" : "white";
        send(session, Map.of("type", "reconnected", "color", color, "roomId", roomId,
                "board", room.board, "turn", turn));
        WebSocketSession opponent = room.sessions[1 - slot];
        if (opponent != null && opponent.isOpen()) send(opponent, Map.of("type", "opponent_back"));
    }

    public synchronized void move(WebSocketSession session, int x, int y) throws IOException {
        GameRoom room = findRoom(session);
        if (room == null || room.finished) return;
        int slot = room.slotOf(session);
        if (slot != room.turn) { send(session, Map.of("type", "error", "message", "not your turn")); return; }
        int color = room.colorOf(session);
        if (!room.applyMove(x, y, color)) { send(session, Map.of("type", "error", "message", "invalid move")); return; }
        String colorName = color == GameRoom.BLACK ? "black" : "white";
        broadcast(room, Map.of("type", "move", "x", x, "y", y, "color", colorName));
        if (room.checkWin(x, y)) {
            room.finished = true;
            broadcast(room, Map.of("type", "win", "color", colorName));
        } else if (room.isFull()) {
            room.finished = true;
            broadcast(room, Map.of("type", "draw"));
        } else {
            room.turn = 1 - room.turn;
        }
    }

    public synchronized void disconnect(WebSocketSession session) throws IOException {
        GameRoom room = findRoom(session);
        if (room == null) return;
        if (room == waitingRoom) { waitingRoom = null; rooms.remove(room.roomId); return; }
        int slot = room.slotOf(session);
        room.sessions[slot] = null;
        room.disconnectedAt = System.currentTimeMillis();
        WebSocketSession opponent = room.sessions[1 - slot];
        if (opponent != null && opponent.isOpen()) send(opponent, Map.of("type", "opponent_left"));
    }

    public synchronized void destroyRoom(String roomId) throws IOException {
        GameRoom room = rooms.remove(roomId);
        if (room == null) return;
        if (room == waitingRoom) waitingRoom = null;
        room.finished = true;
        for (WebSocketSession s : room.sessions)
            if (s != null && s.isOpen()) send(s, Map.of("type", "opponent_left"));
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void cleanup() {
        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(e -> {
            GameRoom r = e.getValue();
            return r.disconnectedAt > 0 && now - r.disconnectedAt > 10 * 60_000;
        });
    }

    private GameRoom findRoom(WebSocketSession session) {
        return rooms.values().stream().filter(r -> r.slotOf(session) >= 0).findFirst().orElse(null);
    }

    private void broadcast(GameRoom room, Object msg) throws IOException {
        for (WebSocketSession s : room.sessions) if (s != null && s.isOpen()) send(s, msg);
    }

    private void send(WebSocketSession session, Object msg) throws IOException {
        session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
    }
}
