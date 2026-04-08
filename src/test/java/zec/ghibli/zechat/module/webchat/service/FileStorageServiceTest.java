package zec.ghibli.zechat.module.webchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zec.ghibli.zechat.module.webchat.model.UploadedFileMeta;
import zec.ghibli.zechat.module.webchat.prop.UploadProp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        UploadProp prop = new UploadProp();
        prop.setDir(tempDir.toString());
        prop.setAllowedTypes(List.of("image/png", "text/plain", "application/pdf"));
        service = new FileStorageService(prop);
    }

    @Test
    void storeAndRetrieve() throws IOException {
        byte[] content = "hello world".getBytes();
        var input = new ByteArrayInputStream(content);

        UploadedFileMeta meta = service.store("test.txt", "text/plain", content.length, input);

        assertNotNull(meta.fileId());
        assertEquals("test.txt", meta.fileName());
        assertEquals("text/plain", meta.mimeType());
        assertEquals(content.length, meta.size());
        assertNotNull(meta.uploadTime());

        Path retrieved = service.getPath(meta.fileId());
        assertTrue(Files.exists(retrieved));
        assertArrayEquals(content, Files.readAllBytes(retrieved));
    }

    @Test
    void rejectDisallowedMimeType() {
        byte[] content = "data".getBytes();
        var input = new ByteArrayInputStream(content);

        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.store("file.exe", "application/x-msdownload", content.length, input));
        assertTrue(ex.getMessage().contains("不支持的文件类型"));
    }

    @Test
    void getPathThrowsForUnknownFileId() {
        assertThrows(IllegalArgumentException.class, () -> service.getPath("nonexistent-id"));
    }
}
