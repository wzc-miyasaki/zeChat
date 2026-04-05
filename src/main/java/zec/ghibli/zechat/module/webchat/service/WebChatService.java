package zec.ghibli.zechat.module.webchat.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Service
public class WebChatService {

    public String chat(String message) {
        return "Echo: " + message;
    }

    public Flux<String> chatStream(String message) {
        String reply = "Echo: " + message;
        return Flux.fromArray(reply.split(""))
                .delayElements(Duration.ofMillis(50));
    }
}
