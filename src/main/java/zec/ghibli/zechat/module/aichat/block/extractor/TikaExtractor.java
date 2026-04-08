package zec.ghibli.zechat.module.aichat.block.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

@Slf4j
@Component
@Order(10)
public class TikaExtractor implements FileContentExtractor {

    private final Tika tika = new Tika();

    @Override
    public boolean supports(String mimeType) {
        // Fallback: supports everything not handled by higher-priority extractors
        return true;
    }

    @Override
    public String extract(Path path, String mimeType) {
        try {
            String content = tika.parseToString(path);
            log.debug("Tika extracted {} chars from {}", content.length(), path.getFileName());
            return content;
        } catch (Exception e) {
            log.warn("Tika extraction failed for {}: {}", path, e.getMessage());
            throw new UncheckedIOException(new IOException("文件内容提取失败: " + path.getFileName(), e));
        }
    }
}
