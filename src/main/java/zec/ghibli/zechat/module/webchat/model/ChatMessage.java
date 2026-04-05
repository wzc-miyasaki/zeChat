package zec.ghibli.zechat.module.webchat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String role;      // "user" or "assistant"
    private String content;
    private LocalDateTime timestamp;

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, LocalDateTime.now());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, LocalDateTime.now());
    }
}
