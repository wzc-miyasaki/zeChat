package zec.ghibli.zechat.module.aichat.prop;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zec.ghibli.zechat.module.aichat.advisor.StripThinkingAdvisor;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai.chat")
public class AiChatProp {
    private static final Logger log = LoggerFactory.getLogger(AiChatProp.class);
    private Map<String, ProviderConfig> providers = new HashMap<>();

    public void init() {
        log.info("Loaded AI Chat Providers:");
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    @Data
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
    }
}
