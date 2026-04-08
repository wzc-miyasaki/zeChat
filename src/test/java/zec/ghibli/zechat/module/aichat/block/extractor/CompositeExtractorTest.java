package zec.ghibli.zechat.module.aichat.block.extractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompositeExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void plainTextExtractorHandlesTextMime() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, World!");

        var extractor = new PlainTextExtractor();
        assertTrue(extractor.supports("text/plain"));
        assertTrue(extractor.supports("text/markdown"));
        assertTrue(extractor.supports("text/csv"));
        assertFalse(extractor.supports("application/pdf"));

        String result = extractor.extract(file, "text/plain");
        assertEquals("Hello, World!", result);
    }

    @Test
    void compositeDispatchesToPlainTextFirst() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "plain text content");

        var composite = new CompositeExtractor(List.of(
                new PlainTextExtractor(),
                new TikaExtractor()
        ));

        String result = composite.extract(file, "text/plain");
        assertEquals("plain text content", result);
    }

    @Test
    void compositeFallsToTikaForPdf() throws IOException {
        Path file = tempDir.resolve("test.pdf");
        Files.writeString(file, "not a real pdf but tika won't crash");

        var composite = new CompositeExtractor(List.of(
                new PlainTextExtractor(),
                new TikaExtractor()
        ));

        assertTrue(new TikaExtractor().supports("application/pdf"));
        String result = composite.extract(file, "application/pdf");
        assertNotNull(result);
    }
}
