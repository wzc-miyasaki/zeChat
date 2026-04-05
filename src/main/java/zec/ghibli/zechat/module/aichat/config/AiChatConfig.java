package zec.ghibli.zechat.module.aichat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zec.ghibli.zechat.module.aichat.advisor.StripThinkingAdvisor;
import zec.ghibli.zechat.module.aichat.prop.AiChatProp;

import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(AiChatProp.class)
public class AiChatConfig {

    @Bean
    public Map<String, ChatClient> chatClients(AiChatProp prop) {
        return prop.getProviders().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> buildChatClient(e.getValue())
                ));
    }

    private ChatClient buildChatClient(AiChatProp.ProviderConfig config) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModel())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new StripThinkingAdvisor())
                .build();
    }
}
