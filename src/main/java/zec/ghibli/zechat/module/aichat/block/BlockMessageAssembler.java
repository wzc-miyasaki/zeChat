package zec.ghibli.zechat.module.aichat.block;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import zec.ghibli.zechat.module.aichat.block.extractor.CompositeExtractor;
import zec.ghibli.zechat.module.webchat.model.block.*;
import zec.ghibli.zechat.module.webchat.service.FileStorageService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockMessageAssembler {

    private final FileStorageService fileStorageService;
    private final CompositeExtractor compositeExtractor;

    public AssembledMessage assemble(BlockMessage message) {
        var textParts = new StringBuilder();
        var mediaList = new ArrayList<Media>();

        for (MessageBlock block : message.blocks()) {
            if (block instanceof TextBlock t) {
                textParts.append(t.text()).append("\n");

            } else if (block instanceof ImageBlock img) {
                mediaList.add(resolveImage(img));

            } else if (block instanceof FileBlock f) {
                Path filePath = fileStorageService.getPath(f.fileId());
                String extracted = compositeExtractor.extract(filePath, f.mimeType());
                textParts.append("[文件: ").append(f.fileName()).append("]\n");
                textParts.append(extracted).append("\n");

            } else {
                log.warn("Unknown block type: {}, skipping", block.getClass().getSimpleName());
            }
        }

        return new AssembledMessage(textParts.toString(), List.copyOf(mediaList));
    }

    private Media resolveImage(ImageBlock img) {
        var mimeType = MimeTypeUtils.parseMimeType(img.mimeType());

        if (img.inlineData() != null && !img.inlineData().isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(img.inlineData());
            return new Media(mimeType, new ByteArrayResource(bytes));
        }

        if (img.fileId() != null && !img.fileId().isBlank()) {
            Path path = fileStorageService.getPath(img.fileId());
            return new Media(mimeType, new FileSystemResource(path));
        }

        throw new IllegalArgumentException("ImageBlock must have either fileId or inlineData");
    }
}
