package zec.ghibli.zechat.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/openapi")
public class OpenapiController {

    private final Map<String, ChatClient> chatClients;

    // Spring 会自动把名为 chatClients 的 Bean 注入进来
    public OpenapiController(Map<String, ChatClient> chatClients) {
        this.chatClients = chatClients;
    }


    @PostMapping("minimax")
    public String chat(@RequestBody String message) {
        ChatClient chatClient = chatClients.get("minimax");
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    @PostMapping("/deepseek")
    public Flux<String> chatStream(@RequestBody String message) {
        ChatClient chatClient = chatClients.get("deepseek");
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }
}
