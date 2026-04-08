package zec.ghibli.zechat.module.aichat.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Flux;
import zec.ghibli.zechat.module.aichat.block.AssembledMessage;
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

    public Flux<String> chatStream(String provider, AssembledMessage assembled) {
        ChatClient chatClient = chatClients.get(provider);
        if (chatClient == null) {
            throw new IllegalArgumentException("Unknown provider: " + provider);
        }

        if (!assembled.hasMedia()) {
            return chatClient.prompt()
                    .system(SysPromptTemplate.DEFAULT)
                    .user(assembled.text())
                    .stream()
                    .content();
        }

        // Multimodal: use fluent API to attach text + media
        return chatClient.prompt()
                .system(SysPromptTemplate.DEFAULT)
                .user(u -> {
                    u.text(assembled.text());
                    assembled.media().forEach(m -> {
                        u.media(m.getMimeType(), new ByteArrayResource(m.getDataAsByteArray()));
                    });
                })
                .stream()
                .content();
    }
}
