package zec.ghibli.zechat.module.game.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zec.ghibli.zechat.module.game.model.UserSession;
import zec.ghibli.zechat.module.game.service.GameRoomService;
import zec.ghibli.zechat.module.game.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameAuthController {

    private final UserService userService;
    private final GameRoomService gameRoomService;

    public GameAuthController(UserService userService, GameRoomService gameRoomService) {
        this.userService = userService;
        this.gameRoomService = gameRoomService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }
        try {
            UserSession session = userService.login(username.trim(), password);
            return ResponseEntity.ok(Map.of("token", session.token(), "username", session.username()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) Map<String, String> body) {
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            String roomId = body != null ? body.get("roomId") : null;
            if (roomId != null) {
                try { gameRoomService.destroyRoom(roomId); } catch (Exception ignored) {}
            }
            userService.logout(token);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
