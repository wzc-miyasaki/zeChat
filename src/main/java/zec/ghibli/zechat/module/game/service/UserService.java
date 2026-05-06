package zec.ghibli.zechat.module.game.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import zec.ghibli.zechat.module.game.model.UserSession;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private static final String SECRET = "zeChat-gomoku-jwt-secret-key-must-be-at-least-32-bytes!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final long EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L;

    private final Map<String, String> credentials = new ConcurrentHashMap<>();
    private final Set<String> activeTokens = ConcurrentHashMap.newKeySet();

    public UserSession login(String username, String password) {
        String stored = credentials.get(username);
        if (stored == null) {
            credentials.put(username, password);
        } else if (!stored.equals(password)) {
            throw new IllegalArgumentException("密码错误");
        }
        String token = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
                .signWith(KEY)
                .compact();
        activeTokens.add(token);
        return new UserSession(username, token);
    }

    public UserSession validate(String token) {
        if (token == null || !activeTokens.contains(token)) return null;
        try {
            String username = Jwts.parser().verifyWith(KEY).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return new UserSession(username, token);
        } catch (Exception e) {
            return null;
        }
    }

    public void logout(String token) {
        activeTokens.remove(token);
    }
}
