package zec.ghibli.zechat.module.aichat.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Flux;
import zec.ghibli.zechat.module.aichat.constant.SysPromptTemplate;

import java.util.Map;

/**
 * Demo service for AI chat. You can implement your AI chat logic here, such as calling external AI APIs, processing responses, etc.
 */
@Service
public class SimpleChatService {
    private final Map<String, ChatClient> chatClients;

    public SimpleChatService(Map<String, ChatClient> chatClients) {
        this.chatClients = chatClients;
    }

    public String chat(String provider, String message) {
        ChatClient chatClient = chatClients.get(provider);
        if (chatClient == null) {
            throw new IllegalArgumentException("Unknown provider: " + provider);
        }
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    public Flux<String> chatStream(String provider, String message) {
        ChatClient chatClient = chatClients.get(provider);
        if (chatClient == null) {
            throw new IllegalArgumentException("Unknown provider: " + provider);
        }
        return chatClient.prompt()
                .system(SysPromptTemplate.DEFAULT)
                .user(message)
                .stream()
                .content();
    }
}
