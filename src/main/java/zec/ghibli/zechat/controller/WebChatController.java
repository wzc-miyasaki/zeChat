package zec.ghibli.zechat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import zec.ghibli.zechat.module.aichat.block.AssembledMessage;
import zec.ghibli.zechat.module.aichat.block.BlockMessageAssembler;
import zec.ghibli.zechat.module.aichat.service.SimpleChatService;
import zec.ghibli.zechat.module.webchat.model.ChatMessage;
import zec.ghibli.zechat.module.webchat.model.block.BlockMessage;
import zec.ghibli.zechat.module.webchat.service.WebChatService;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class WebChatController {

    private final WebChatService webChatService;
    private final SimpleChatService simpleChatService;
    private final BlockMessageAssembler blockMessageAssembler;

    @PostMapping
    public ChatMessage chat(@RequestBody String message) {
        String reply = webChatService.chat(message);
        return ChatMessage.assistant(reply);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody String message) {
        return simpleChatService.chatStream("minimax", message);
    }

    @PostMapping(value = "/stream/v3", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStreamV3(@RequestBody BlockMessage blockMessage) {
        try {
            AssembledMessage assembled = blockMessageAssembler.assemble(blockMessage);
            return simpleChatService.chatStream("minimax", assembled);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
