package zec.ghibli.zechat.module.aichat.block.extractor;

import java.nio.file.Path;

public interface FileContentExtractor {
    boolean supports(String mimeType);
    String extract(Path path, String mimeType);
}
