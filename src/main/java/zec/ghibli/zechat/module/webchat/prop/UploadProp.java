package zec.ghibli.zechat.module.webchat.prop;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "zechat.upload")
public class UploadProp {
    private String dir = "./uploads";
    private List<String> allowedTypes = List.of();
}
