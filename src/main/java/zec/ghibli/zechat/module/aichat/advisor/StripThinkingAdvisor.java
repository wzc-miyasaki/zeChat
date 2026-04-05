package zec.ghibli.zechat.module.aichat.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.regex.Pattern;

@Slf4j
public class StripThinkingAdvisor implements BaseAdvisor {

    private static final Pattern THINK_PATTERN =
            Pattern.compile("(?s)<think>.*?</think>\\s*");

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return BaseAdvisor.super.adviseStream(chatClientRequest, streamAdvisorChain);
    }

    @Override
    public String getName() {
        return "StripThinkingAdvisor";
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 1. 获取原始用户输入
        String originalUserText = chatClientRequest.prompt().getUserMessage().getText();
        log.info("Original user input: {}", originalUserText);
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        ChatResponse chatResponse = chatClientResponse.chatResponse();
        if (chatResponse == null) {
            log.warn("Chat response is null");
            return chatClientResponse;
        }

        AssistantMessage msg = chatResponse.getResult().getOutput();
        String originalTxt = msg.getText();
        // 2. 使用正则表达式去除 <think> 标签及其内容
        String cleanedTxt = THINK_PATTERN.matcher(originalTxt).replaceAll("");
        log.info("Original assistant response: {}", originalTxt);
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
