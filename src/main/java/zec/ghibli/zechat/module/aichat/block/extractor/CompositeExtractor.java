package zec.ghibli.zechat.module.aichat.block.extractor;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class CompositeExtractor {

    private final List<FileContentExtractor> extractors;

    public CompositeExtractor(List<FileContentExtractor> extractors) {
        this.extractors = extractors;
    }

    public String extract(Path path, String mimeType) {
        for (FileContentExtractor extractor : extractors) {
            if (extractor.supports(mimeType)) {
                return extractor.extract(path, mimeType);
            }
        }
        throw new IllegalArgumentException("No extractor found for MIME type: " + mimeType);
    }
}
