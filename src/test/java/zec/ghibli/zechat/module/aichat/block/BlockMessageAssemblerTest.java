package zec.ghibli.zechat.module.aichat.block;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zec.ghibli.zechat.module.aichat.block.extractor.CompositeExtractor;
import zec.ghibli.zechat.module.aichat.block.extractor.PlainTextExtractor;
import zec.ghibli.zechat.module.webchat.model.block.*;
import zec.ghibli.zechat.module.webchat.prop.UploadProp;
import zec.ghibli.zechat.module.webchat.service.FileStorageService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockMessageAssemblerTest {

    @TempDir
    java.nio.file.Path tempDir;

    private BlockMessageAssembler assembler;
    private FileStorageService fileStorage;

    @BeforeEach
    void setUp() {
        UploadProp prop = new UploadProp();
        prop.setDir(tempDir.toString());
        prop.setAllowedTypes(List.of("text/plain", "image/png"));
        fileStorage = new FileStorageService(prop);

        var extractor = new CompositeExtractor(List.of(new PlainTextExtractor()));
        assembler = new BlockMessageAssembler(fileStorage, extractor);
    }

    @Test
    void assembleTextOnlyBlocks() {
        var message = new BlockMessage(List.of(
                new TextBlock(1, "Hello"),
                new TextBlock(2, "World")
        ));

        AssembledMessage result = assembler.assemble(message);

        assertTrue(result.text().contains("Hello"));
        assertTrue(result.text().contains("World"));
        assertTrue(result.media().isEmpty());
    }

    @Test
    void assembleWithInlineImage() {
        byte[] fakeImage = new byte[]{1, 2, 3, 4};
        String base64 = Base64.getEncoder().encodeToString(fakeImage);

        var message = new BlockMessage(List.of(
                new TextBlock(1, "Look at this:"),
                new ImageBlock(2, null, "image/png", base64)
        ));

        AssembledMessage result = assembler.assemble(message);

        assertTrue(result.text().contains("Look at this:"));
        assertEquals(1, result.media().size());
    }

    @Test
    void assembleWithFileBlock() throws IOException {
        byte[] content = "extracted file content".getBytes();
        var meta = fileStorage.store("data.txt", "text/plain", content.length,
                new ByteArrayInputStream(content));

        var message = new BlockMessage(List.of(
                new TextBlock(1, "Analyze:"),
                new FileBlock(2, meta.fileId(), "data.txt", "text/plain", content.length)
        ));

        AssembledMessage result = assembler.assemble(message);

        assertTrue(result.text().contains("Analyze:"));
        assertTrue(result.text().contains("[文件: data.txt]"));
        assertTrue(result.text().contains("extracted file content"));
        assertTrue(result.media().isEmpty());
    }
}
