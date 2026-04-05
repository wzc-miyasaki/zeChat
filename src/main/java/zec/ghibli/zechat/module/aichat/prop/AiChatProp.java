package zec.ghibli.zechat.module.aichat.prop;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai.chat")
@Slf4j
public class AiChatProp {

    private Map<String, ProviderConfig> providers = new HashMap<>();

    public void init() {
        log.info("Loaded AI Chat Providers:");
    }

    @Data
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
    }
}
