package zec.ghibli.zechat.module.aichat.block.extractor;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Order(1)
public class PlainTextExtractor implements FileContentExtractor {

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && mimeType.startsWith("text/");
    }

    @Override
    public String extract(Path path, String mimeType) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read text file: " + path, e);
        }
    }
}
