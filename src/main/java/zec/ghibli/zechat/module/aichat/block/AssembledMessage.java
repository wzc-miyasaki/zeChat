package zec.ghibli.zechat.module.aichat.block;

import org.springframework.ai.content.Media;

import java.util.List;

public record AssembledMessage(String text, List<Media> media) {
    public boolean hasMedia() {
        return media != null && !media.isEmpty();
    }
}
