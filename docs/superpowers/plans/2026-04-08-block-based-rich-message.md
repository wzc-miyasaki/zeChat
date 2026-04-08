# Block-Based Rich Message System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add block-based rich message support (text + image + file) to zeChat, enabling multimodal chat via Spring AI.

**Architecture:** Frontend `contenteditable` editor serializes mixed content into a `BlockMessage` JSON. Backend `BlockMessageAssembler` processes each block type: text is concatenated, images become Spring AI `Media` objects, files are text-extracted via Tika. The assembled result feeds into `ChatClient` for multimodal LLM calls via SSE streaming.

**Tech Stack:** Spring Boot 3.5.9, Spring AI 1.1.4, Java 21 records, Jackson polymorphic deserialization, Apache Tika 2.9.2, vanilla JS `contenteditable`.

---

## File Structure

### New files (15 source + 4 test)

| File | Responsibility |
|------|---------------|
| `src/main/java/.../module/webchat/model/block/MessageBlock.java` | Jackson polymorphic interface for all block types |
| `src/main/java/.../module/webchat/model/block/TextBlock.java` | Text block record |
| `src/main/java/.../module/webchat/model/block/ImageBlock.java` | Image block record (fileId or inlineData) |
| `src/main/java/.../module/webchat/model/block/FileBlock.java` | File block record |
| `src/main/java/.../module/webchat/model/block/BlockMessage.java` | Top-level request DTO wrapping List\<MessageBlock\> |
| `src/main/java/.../module/webchat/model/UploadedFileMeta.java` | Upload response record |
| `src/main/java/.../module/webchat/prop/UploadProp.java` | Config properties for upload (dir, allowed-types) |
| `src/main/java/.../module/webchat/service/FileStorageService.java` | Store/retrieve uploaded files |
| `src/main/java/.../controller/FileController.java` | Upload + file-serve endpoints |
| `src/main/java/.../module/aichat/block/AssembledMessage.java` | Assembler output: text + List\<Media\> |
| `src/main/java/.../module/aichat/block/BlockMessageAssembler.java` | Block → AssembledMessage conversion |
| `src/main/java/.../module/aichat/block/extractor/FileContentExtractor.java` | Strategy interface |
| `src/main/java/.../module/aichat/block/extractor/PlainTextExtractor.java` | text/* extraction |
| `src/main/java/.../module/aichat/block/extractor/TikaExtractor.java` | PDF/Office/fallback extraction |
| `src/main/java/.../module/aichat/block/extractor/CompositeExtractor.java` | Dispatches to first matching extractor |
| `src/main/resources/static/chat3.html` | Rich text editor frontend |
| `src/test/java/.../module/webchat/model/block/BlockMessageDeserializationTest.java` | JSON → BlockMessage test |
| `src/test/java/.../module/webchat/service/FileStorageServiceTest.java` | Upload store/retrieve test |
| `src/test/java/.../module/aichat/block/extractor/CompositeExtractorTest.java` | Extractor dispatch test |
| `src/test/java/.../module/aichat/block/BlockMessageAssemblerTest.java` | Assembler integration test |

### Modified files (4)

| File | Change |
|------|--------|
| `pom.xml` | Add Tika dependencies |
| `src/main/resources/application.yml` | Upload config + multipart limits |
| `src/main/java/.../module/aichat/service/SimpleChatService.java` | New `chatStream(provider, AssembledMessage)` overload |
| `src/main/java/.../controller/WebChatController.java` | New `/stream/v3` endpoint + wire upload/files to FileController |

**Base package path:** `src/main/java/zec/ghibli/zechat` (abbreviated as `...` above)

---

## Task 1: Add Dependencies and Configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/zec/ghibli/zechat/module/webchat/prop/UploadProp.java`

- [ ] **Step 1: Add Tika dependencies to pom.xml**

Add inside `<dependencies>`, after the existing `spring-boot-starter-test`:

```xml
        <dependency>
            <groupId>org.apache.tika</groupId>
            <artifactId>tika-core</artifactId>
            <version>2.9.2</version>
        </dependency>
        <dependency>
            <groupId>org.apache.tika</groupId>
            <artifactId>tika-parsers-standard-package</artifactId>
            <version>2.9.2</version>
        </dependency>
```

- [ ] **Step 2: Add upload configuration to application.yml**

Append at the end of `application.yml`:

```yaml

zechat:
  upload:
    dir: ./uploads
    allowed-types:
      - image/png
      - image/jpeg
      - image/gif
      - image/webp
      - application/pdf
      - application/msword
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      - application/vnd.ms-excel
      - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
      - text/plain
      - text/markdown
      - text/csv

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

Note: The existing `spring:` block already has `application:` and `ai:` keys. Merge the `servlet:` block under the existing `spring:` key rather than creating a duplicate.

- [ ] **Step 3: Create UploadProp configuration properties class**

Create `src/main/java/zec/ghibli/zechat/module/webchat/prop/UploadProp.java`:

```java
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
```

- [ ] **Step 4: Verify build compiles**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw compile -q`
Expected: BUILD SUCCESS (no errors)

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.yml src/main/java/zec/ghibli/zechat/module/webchat/prop/UploadProp.java
git commit -m "feat(block): add Tika dependencies and upload configuration"
```

---

## Task 2: Block Protocol Models

**Files:**
- Create: `src/main/java/zec/ghibli/zechat/module/webchat/model/block/MessageBlock.java`
- Create: `src/main/java/zec/ghibli/zechat/module/webchat/model/block/TextBlock.java`
- Create: `src/main/java/zec/ghibli/zechat/module/webchat/model/block/ImageBlock.java`
- Create: `src/main/java/zec/ghibli/zechat/module/webchat/model/block/FileBlock.java`
- Create: `src/main/java/zec/ghibli/zechat/module/webchat/model/block/BlockMessage.java`
- Test: `src/test/java/zec/ghibli/zechat/module/webchat/model/block/BlockMessageDeserializationTest.java`

- [ ] **Step 1: Write the deserialization test**

Create `src/test/java/zec/ghibli/zechat/module/webchat/model/block/BlockMessageDeserializationTest.java`:

```java
package zec.ghibli.zechat.module.webchat.model.block;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockMessageDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializeMixedBlocks() throws Exception {
        String json = """
                {
                  "blocks": [
                    { "id": 1, "type": "TEXT", "text": "Hello" },
                    { "id": 2, "type": "IMAGE", "fileId": "abc", "mimeType": "image/png" },
                    { "id": 3, "type": "FILE", "fileId": "def", "fileName": "report.pdf", "mimeType": "application/pdf", "size": 1024 }
                  ]
                }
                """;

        BlockMessage message = mapper.readValue(json, BlockMessage.class);

        assertEquals(3, message.blocks().size());

        assertInstanceOf(TextBlock.class, message.blocks().get(0));
        TextBlock text = (TextBlock) message.blocks().get(0);
        assertEquals(1, text.id());
        assertEquals("Hello", text.text());

        assertInstanceOf(ImageBlock.class, message.blocks().get(1));
        ImageBlock image = (ImageBlock) message.blocks().get(1);
        assertEquals(2, image.id());
        assertEquals("abc", image.fileId());
        assertEquals("image/png", image.mimeType());

        assertInstanceOf(FileBlock.class, message.blocks().get(2));
        FileBlock file = (FileBlock) message.blocks().get(2);
        assertEquals(3, file.id());
        assertEquals("def", file.fileId());
        assertEquals("report.pdf", file.fileName());
        assertEquals(1024, file.size());
    }

    @Test
    void deserializeInlineImageBlock() throws Exception {
        String json = """
                {
                  "blocks": [
                    { "id": 1, "type": "IMAGE", "mimeType": "image/png", "inlineData": "iVBOR==" }
                  ]
                }
                """;

        BlockMessage message = mapper.readValue(json, BlockMessage.class);
        ImageBlock image = (ImageBlock) message.blocks().get(0);
        assertNull(image.fileId());
        assertEquals("iVBOR==", image.inlineData());
    }

    @Test
    void serializeRoundTrip() throws Exception {
        BlockMessage original = new BlockMessage(java.util.List.of(
                new TextBlock(1, "test text"),
                new ImageBlock(2, "fid", "image/jpeg", null)
        ));

        String json = mapper.writeValueAsString(original);
        BlockMessage deserialized = mapper.readValue(json, BlockMessage.class);

        assertEquals(2, deserialized.blocks().size());
        assertInstanceOf(TextBlock.class, deserialized.blocks().get(0));
        assertInstanceOf(ImageBlock.class, deserialized.blocks().get(1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -Dtest=BlockMessageDeserializationTest -pl . -q 2>&1 | tail -5`
Expected: COMPILATION ERROR (classes don't exist yet)

- [ ] **Step 3: Create MessageBlock interface**

Create `src/main/java/zec/ghibli/zechat/module/webchat/model/block/MessageBlock.java`:

```java
package zec.ghibli.zechat.module.webchat.model.block;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextBlock.class, name = "TEXT"),
        @JsonSubTypes.Type(value = ImageBlock.class, name = "IMAGE"),
        @JsonSubTypes.Type(value = FileBlock.class, name = "FILE"),
})
public interface MessageBlock {
    int id();
    String type();
}
```

- [ ] **Step 4: Create TextBlock record**

Create `src/main/java/zec/ghibli/zechat/module/webchat/model/block/TextBlock.java`:

```java
package zec.ghibli.zechat.module.webchat.model.block;

public record TextBlock(int id, String text) implements MessageBlock {
    @Override
    public String type() {
        return "TEXT";
    }
}
```

- [ ] **Step 5: Create ImageBlock record**

Create `src/main/java/zec/ghibli/zechat/module/webchat/model/block/ImageBlock.java`:

```java
package zec.ghibli.zechat.module.webchat.model.block;

public record ImageBlock(int id, String fileId, String mimeType, String inlineData) implements MessageBlock {
    @Override
    public String type() {
        return "IMAGE";
    }
}
```

- [ ] **Step 6: Create FileBlock record**

Create `src/main/java/zec/ghibli/zechat/module/webchat/model/block/FileBlock.java`:

```java
package zec.ghibli.zechat.module.webchat.model.block;

public record FileBlock(int id, String fileId, String fileName, String mimeType, long size) implements MessageBlock {
    @Override
    public String type() {
        return "FILE";
    }
}
```

- [ ] **Step 7: Create BlockMessage DTO**

Create `src/main/java/zec/ghibli/zechat/module/webchat/model/block/BlockMessage.java`:

```java
package zec.ghibli.zechat.module.webchat.model.block;

import java.util.List;

public record BlockMessage(List<MessageBlock> blocks) {
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -Dtest=BlockMessageDeserializationTest -pl . -q`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 9: Commit**

```bash
git add src/main/java/zec/ghibli/zechat/module/webchat/model/block/ src/test/java/zec/ghibli/zechat/module/webchat/model/block/
git commit -m "feat(block): add block protocol models with Jackson polymorphic deserialization"
```

---

## Task 3: File Upload Infrastructure

**Files:**
- Create: `src/main/java/zec/ghibli/zechat/module/webchat/model/UploadedFileMeta.java`
- Create: `src/main/java/zec/ghibli/zechat/module/webchat/service/FileStorageService.java`
- Test: `src/test/java/zec/ghibli/zechat/module/webchat/service/FileStorageServiceTest.java`

- [ ] **Step 1: Write the FileStorageService test**

Create `src/test/java/zec/ghibli/zechat/module/webchat/service/FileStorageServiceTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -Dtest=FileStorageServiceTest -pl . -q 2>&1 | tail -5`
Expected: COMPILATION ERROR

- [ ] **Step 3: Create UploadedFileMeta record**

Create `src/main/java/zec/ghibli/zechat/module/webchat/model/UploadedFileMeta.java`:

```java
package zec.ghibli.zechat.module.webchat.model;

import java.time.Instant;

public record UploadedFileMeta(
        String fileId,
        String fileName,
        String mimeType,
        long size,
        Instant uploadTime
) {
}
```

- [ ] **Step 4: Create FileStorageService**

Create `src/main/java/zec/ghibli/zechat/module/webchat/service/FileStorageService.java`:

```java
package zec.ghibli.zechat.module.webchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import zec.ghibli.zechat.module.webchat.model.UploadedFileMeta;
import zec.ghibli.zechat.module.webchat.prop.UploadProp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@EnableConfigurationProperties(UploadProp.class)
public class FileStorageService {

    private final UploadProp prop;
    private final Path baseDir;
    private final Map<String, Path> fileIndex = new ConcurrentHashMap<>();

    public FileStorageService(UploadProp prop) {
        this.prop = prop;
        this.baseDir = Path.of(prop.getDir());
    }

    public UploadedFileMeta store(String fileName, String mimeType, long size, InputStream data) throws IOException {
        if (!prop.getAllowedTypes().contains(mimeType)) {
            throw new IllegalArgumentException("不支持的文件类型: " + mimeType);
        }

        String fileId = UUID.randomUUID().toString();
        String ext = extractExtension(fileName);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path dir = baseDir.resolve(datePath);
        Files.createDirectories(dir);

        Path target = dir.resolve(fileId + ext);
        Files.copy(data, target);
        fileIndex.put(fileId, target);

        log.info("Stored file: {} -> {}", fileId, target);
        return new UploadedFileMeta(fileId, fileName, mimeType, size, Instant.now());
    }

    public Path getPath(String fileId) {
        Path path = fileIndex.get(fileId);
        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException("文件不存在: " + fileId);
        }
        return path;
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -Dtest=FileStorageServiceTest -pl . -q`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 6: Commit**

```bash
git add src/main/java/zec/ghibli/zechat/module/webchat/model/UploadedFileMeta.java src/main/java/zec/ghibli/zechat/module/webchat/service/FileStorageService.java src/test/java/zec/ghibli/zechat/module/webchat/service/FileStorageServiceTest.java
git commit -m "feat(block): add file storage service with MIME validation"
```

---

## Task 4: File Upload Controller

**Files:**
- Create: `src/main/java/zec/ghibli/zechat/controller/FileController.java`

- [ ] **Step 1: Create FileController**

Create `src/main/java/zec/ghibli/zechat/controller/FileController.java`:

```java
package zec.ghibli.zechat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import zec.ghibli.zechat.module.webchat.model.UploadedFileMeta;
import zec.ghibli.zechat.module.webchat.service.FileStorageService;

import java.io.IOException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public UploadedFileMeta upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        try {
            return fileStorageService.store(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    file.getInputStream()
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件上传失败");
        }
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> serve(@PathVariable String fileId) {
        try {
            Path path = fileStorageService.getPath(fileId);
            Resource resource = new FileSystemResource(path);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/zec/ghibli/zechat/controller/FileController.java
git commit -m "feat(block): add file upload and serve controller"
```

---

## Task 5: File Content Extractors

**Files:**
- Create: `src/main/java/zec/ghibli/zechat/module/aichat/block/extractor/FileContentExtractor.java`
- Create: `src/main/java/zec/ghibli/zechat/module/aichat/block/extractor/PlainTextExtractor.java`
- Create: `src/main/java/zec/ghibli/zechat/module/aichat/block/extractor/TikaExtractor.java`
- Create: `src/main/java/zec/ghibli/zechat/module/aichat/block/extractor/CompositeExtractor.java`
- Test: `src/test/java/zec/ghibli/zechat/module/aichat/block/extractor/CompositeExtractorTest.java`

- [ ] **Step 1: Write the extractor test**

Create `src/test/java/zec/ghibli/zechat/module/aichat/block/extractor/CompositeExtractorTest.java`:

```java
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
        // Create a minimal file (Tika will try to parse; content not critical for this test)
        Path file = tempDir.resolve("test.pdf");
        Files.writeString(file, "not a real pdf but tika won't crash");

        var composite = new CompositeExtractor(List.of(
                new PlainTextExtractor(),
                new TikaExtractor()
        ));

        // TikaExtractor.supports("application/pdf") should be true
        assertTrue(new TikaExtractor().supports("application/pdf"));

        // extract should not throw — Tika handles gracefully
        String result = composite.extract(file, "application/pdf");
        assertNotNull(result);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -Dtest=CompositeExtractorTest -pl . -q 2>&1 | tail -5`
Expected: COMPILATION ERROR

- [ ] **Step 3: Create FileContentExtractor interface**

Create `src/main/java/zec/ghibli/zechat/module/aichat/block/extractor/FileContentExtractor.java`:

```java
package zec.ghibli.zechat.module.aichat.block.extractor;

import java.nio.file.Path;

public interface FileContentExtractor {
    boolean supports(String mimeType);
    String extract(Path path, String mimeType);
}
```

- [ ] **Step 4: Create PlainTextExtractor**

Create `src/main/java/zec/ghibli/zechat/module/aichat/block/extractor/PlainTextExtractor.java`:

```java
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
```

- [ ] **Step 5: Create TikaExtractor**

Create `src/main/java/zec/ghibli/zechat/module/aichat/block/extractor/TikaExtractor.java`:

```java
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
```

- [ ] **Step 6: Create CompositeExtractor**

Create `src/main/java/zec/ghibli/zechat/module/aichat/block/extractor/CompositeExtractor.java`:

```java
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
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -Dtest=CompositeExtractorTest -pl . -q`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 8: Commit**

```bash
git add src/main/java/zec/ghibli/zechat/module/aichat/block/extractor/ src/test/java/zec/ghibli/zechat/module/aichat/block/extractor/
git commit -m "feat(block): add file content extractors with Tika fallback"
```

---

## Task 6: BlockMessageAssembler

**Files:**
- Create: `src/main/java/zec/ghibli/zechat/module/aichat/block/AssembledMessage.java`
- Create: `src/main/java/zec/ghibli/zechat/module/aichat/block/BlockMessageAssembler.java`
- Test: `src/test/java/zec/ghibli/zechat/module/aichat/block/BlockMessageAssemblerTest.java`

- [ ] **Step 1: Write the assembler test**

Create `src/test/java/zec/ghibli/zechat/module/aichat/block/BlockMessageAssemblerTest.java`:

```java
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
        // Upload a text file first
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -Dtest=BlockMessageAssemblerTest -pl . -q 2>&1 | tail -5`
Expected: COMPILATION ERROR

- [ ] **Step 3: Create AssembledMessage record**

Create `src/main/java/zec/ghibli/zechat/module/aichat/block/AssembledMessage.java`:

```java
package zec.ghibli.zechat.module.aichat.block;

import org.springframework.ai.model.Media;

import java.util.List;

public record AssembledMessage(String text, List<Media> media) {
    public boolean hasMedia() {
        return media != null && !media.isEmpty();
    }
}
```

- [ ] **Step 4: Create BlockMessageAssembler**

Create `src/main/java/zec/ghibli/zechat/module/aichat/block/BlockMessageAssembler.java`:

```java
package zec.ghibli.zechat.module.aichat.block;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.Media;
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
            return new Media(mimeType, bytes);
        }

        if (img.fileId() != null && !img.fileId().isBlank()) {
            Path path = fileStorageService.getPath(img.fileId());
            return new Media(mimeType, new FileSystemResource(path));
        }

        throw new IllegalArgumentException("ImageBlock must have either fileId or inlineData");
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -Dtest=BlockMessageAssemblerTest -pl . -q`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 6: Commit**

```bash
git add src/main/java/zec/ghibli/zechat/module/aichat/block/ src/test/java/zec/ghibli/zechat/module/aichat/block/
git commit -m "feat(block): add BlockMessageAssembler with multimodal support"
```

---

## Task 7: Service and Controller Integration

**Files:**
- Modify: `src/main/java/zec/ghibli/zechat/module/aichat/service/SimpleChatService.java`
- Modify: `src/main/java/zec/ghibli/zechat/controller/WebChatController.java`

- [ ] **Step 1: Add chatStream overload to SimpleChatService**

In `src/main/java/zec/ghibli/zechat/module/aichat/service/SimpleChatService.java`, add these imports at the top:

```java
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import zec.ghibli.zechat.module.aichat.block.AssembledMessage;
```

Then add this new method after the existing `chatStream`:

```java
    public Flux<String> chatStream(String provider, AssembledMessage assembled) {
        ChatClient chatClient = chatClients.get(provider);
        if (chatClient == null) {
            throw new IllegalArgumentException("Unknown provider: " + provider);
        }

        if (!assembled.hasMedia()) {
            // Text-only: use existing path
            return chatClient.prompt()
                    .system(SysPromptTemplate.DEFAULT)
                    .user(assembled.text())
                    .stream()
                    .content();
        }

        // Multimodal: build UserMessage with Media, use Prompt
        UserMessage userMessage = new UserMessage(assembled.text(), assembled.media());
        Prompt prompt = new Prompt(java.util.List.of(
                new SystemMessage(SysPromptTemplate.DEFAULT),
                userMessage
        ));

        return chatClient.prompt(prompt)
                .stream()
                .content();
    }
```

- [ ] **Step 2: Add /stream/v3 endpoint to WebChatController**

In `src/main/java/zec/ghibli/zechat/controller/WebChatController.java`, add these imports:

```java
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zec.ghibli.zechat.module.aichat.block.AssembledMessage;
import zec.ghibli.zechat.module.aichat.block.BlockMessageAssembler;
import zec.ghibli.zechat.module.webchat.model.block.BlockMessage;
import zec.ghibli.zechat.module.webchat.model.block.ImageBlock;
```

Add `BlockMessageAssembler` as a constructor dependency (add field):

```java
    private final BlockMessageAssembler blockMessageAssembler;
```

Then add the new endpoint method:

```java
    @PostMapping(value = "/stream/v3", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStreamV3(@RequestBody BlockMessage blockMessage) {
        try {
            AssembledMessage assembled = blockMessageAssembler.assemble(blockMessage);
            return simpleChatService.chatStream("minimax", assembled);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
```

- [ ] **Step 3: Verify build compiles**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw compile -q`
Expected: BUILD SUCCESS

If `chatClient.prompt(Prompt)` doesn't compile (API mismatch), change the multimodal path in SimpleChatService to use the fluent user spec approach instead:

```java
        // Alternative if prompt(Prompt) is not available:
        return chatClient.prompt()
                .system(SysPromptTemplate.DEFAULT)
                .user(u -> {
                    u.text(assembled.text());
                    assembled.media().forEach(m -> u.media(m.getMimeType(), m.getData()));
                })
                .stream()
                .content();
```

- [ ] **Step 4: Run all tests**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -q`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/java/zec/ghibli/zechat/module/aichat/service/SimpleChatService.java src/main/java/zec/ghibli/zechat/controller/WebChatController.java
git commit -m "feat(block): integrate block message pipeline into chat endpoints"
```

---

## Task 8: Frontend Rich Text Editor

**Files:**
- Create: `src/main/resources/static/chat3.html`

- [ ] **Step 1: Create chat3.html**

Create `src/main/resources/static/chat3.html`:

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>zeChat - Rich</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }

        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: #f5f5f5;
            height: 100vh;
            display: flex;
            flex-direction: column;
        }

        .header {
            background: #fff;
            padding: 16px 20px;
            border-bottom: 1px solid #e0e0e0;
            font-size: 18px;
            font-weight: 600;
            color: #333;
            text-align: center;
        }

        .messages {
            flex: 1;
            overflow-y: auto;
            padding: 20px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .message {
            max-width: 75%;
            padding: 12px 16px;
            border-radius: 12px;
            line-height: 1.5;
            word-wrap: break-word;
            font-size: 15px;
        }

        .message.user {
            align-self: flex-end;
            background: #007aff;
            color: #fff;
            border-bottom-right-radius: 4px;
        }

        .message.user img {
            max-width: 200px;
            border-radius: 8px;
            margin: 4px 0;
            display: block;
        }

        .message.user .file-tag {
            display: inline-block;
            background: rgba(255,255,255,0.2);
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 13px;
            margin: 2px 0;
        }

        .message.assistant {
            align-self: flex-start;
            background: #fff;
            color: #333;
            border-bottom-left-radius: 4px;
            box-shadow: 0 1px 2px rgba(0,0,0,0.1);
            white-space: pre-wrap;
        }

        .message.assistant .cursor {
            display: inline-block;
            width: 2px;
            height: 1em;
            background: #333;
            animation: blink 0.8s infinite;
            vertical-align: text-bottom;
            margin-left: 1px;
        }

        @keyframes blink {
            0%, 50% { opacity: 1; }
            51%, 100% { opacity: 0; }
        }

        .input-area {
            background: #fff;
            border-top: 1px solid #e0e0e0;
            padding: 12px 20px;
        }

        .editor-wrapper {
            border: 1px solid #ddd;
            border-radius: 8px;
            overflow: hidden;
            transition: border-color 0.2s;
        }

        .editor-wrapper:focus-within {
            border-color: #007aff;
        }

        #editor {
            min-height: 42px;
            max-height: 160px;
            overflow-y: auto;
            padding: 10px 14px;
            font-size: 15px;
            font-family: inherit;
            outline: none;
            line-height: 1.5;
        }

        #editor:empty::before {
            content: attr(data-placeholder);
            color: #999;
        }

        #editor img {
            max-width: 120px;
            max-height: 120px;
            border-radius: 6px;
            vertical-align: middle;
            margin: 2px;
        }

        #editor .file-chip {
            display: inline-block;
            background: #f0f0f0;
            border: 1px solid #ddd;
            border-radius: 6px;
            padding: 2px 8px 2px 6px;
            font-size: 13px;
            margin: 2px;
            vertical-align: middle;
            cursor: default;
            user-select: none;
        }

        #editor .file-chip .remove-chip {
            cursor: pointer;
            margin-left: 4px;
            color: #999;
        }

        #editor .file-chip .remove-chip:hover {
            color: #f00;
        }

        .toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 8px 10px;
            border-top: 1px solid #f0f0f0;
        }

        .toolbar button {
            border: none;
            border-radius: 6px;
            padding: 6px 16px;
            font-size: 14px;
            cursor: pointer;
            transition: background 0.2s;
        }

        .attach-btn {
            background: #f0f0f0;
            color: #555;
        }
        .attach-btn:hover { background: #e0e0e0; }

        .send-btn {
            background: #007aff;
            color: #fff;
        }
        .send-btn:hover { background: #005ecb; }
        .send-btn:disabled { background: #b0b0b0; cursor: not-allowed; }

        .uploading-indicator {
            font-size: 12px;
            color: #999;
            padding: 4px 0;
        }

        @media (max-width: 600px) {
            .message { max-width: 85%; }
            .input-area { padding: 8px 12px; }
        }
    </style>
</head>
<body>
    <div class="header">zeChat</div>
    <div class="messages" id="messages"></div>
    <div class="input-area">
        <div class="editor-wrapper">
            <div id="editor" contenteditable="true" data-placeholder="输入消息..."></div>
            <div class="toolbar">
                <div>
                    <button class="attach-btn" onclick="triggerFileUpload()">附件</button>
                    <span id="uploadStatus" class="uploading-indicator"></span>
                </div>
                <button class="send-btn" id="sendBtn" onclick="sendMessage()">发送</button>
            </div>
        </div>
        <input type="file" id="fileInput" hidden multiple
               accept="image/png,image/jpeg,image/gif,image/webp,application/pdf,.doc,.docx,.xls,.xlsx,.txt,.md,.csv">
    </div>

    <script>
        const messagesEl = document.getElementById('messages');
        const editor = document.getElementById('editor');
        const sendBtn = document.getElementById('sendBtn');
        const fileInput = document.getElementById('fileInput');
        const uploadStatus = document.getElementById('uploadStatus');

        const IMAGE_INLINE_LIMIT = 1024 * 1024; // 1MB

        // --- Enter to send, Shift+Enter for newline ---
        editor.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });

        // --- Paste handler (images from clipboard) ---
        editor.addEventListener('paste', (e) => {
            const items = e.clipboardData?.items;
            if (!items) return;

            for (const item of items) {
                if (item.type.startsWith('image/')) {
                    e.preventDefault();
                    const file = item.getAsFile();
                    handleImageFile(file);
                    return;
                }
            }
            // Allow default paste for text
        });

        // --- Drag and drop handler ---
        editor.addEventListener('dragover', (e) => {
            e.preventDefault();
            editor.style.background = '#f0f8ff';
        });

        editor.addEventListener('dragleave', () => {
            editor.style.background = '';
        });

        editor.addEventListener('drop', (e) => {
            e.preventDefault();
            editor.style.background = '';
            const files = e.dataTransfer?.files;
            if (!files) return;

            for (const file of files) {
                if (file.type.startsWith('image/')) {
                    handleImageFile(file);
                } else {
                    handleDocumentFile(file);
                }
            }
        });

        // --- File input handler ---
        fileInput.addEventListener('change', () => {
            for (const file of fileInput.files) {
                if (file.type.startsWith('image/')) {
                    handleImageFile(file);
                } else {
                    handleDocumentFile(file);
                }
            }
            fileInput.value = '';
        });

        function triggerFileUpload() {
            fileInput.click();
        }

        // --- Image handling ---
        async function handleImageFile(file) {
            if (file.size < IMAGE_INLINE_LIMIT) {
                // Small image: inline as base64
                const reader = new FileReader();
                reader.onload = () => {
                    const base64 = reader.result.split(',')[1];
                    const img = document.createElement('img');
                    img.src = reader.result;
                    img.dataset.inlineData = base64;
                    img.dataset.mimeType = file.type;
                    img.contentEditable = 'false';
                    editor.appendChild(img);
                    placeCaretAtEnd();
                };
                reader.readAsDataURL(file);
            } else {
                // Large image: upload first
                showUploading(file.name);
                const meta = await uploadFile(file);
                hideUploading();
                if (meta) {
                    const img = document.createElement('img');
                    img.src = '/api/chat/files/' + meta.fileId;
                    img.dataset.fileId = meta.fileId;
                    img.dataset.mimeType = meta.mimeType;
                    img.contentEditable = 'false';
                    editor.appendChild(img);
                    placeCaretAtEnd();
                }
            }
        }

        // --- Document file handling ---
        async function handleDocumentFile(file) {
            showUploading(file.name);
            const meta = await uploadFile(file);
            hideUploading();
            if (meta) {
                insertFileChip(meta);
            }
        }

        function insertFileChip(meta) {
            const chip = document.createElement('span');
            chip.className = 'file-chip';
            chip.contentEditable = 'false';
            chip.dataset.fileId = meta.fileId;
            chip.dataset.fileName = meta.fileName;
            chip.dataset.mimeType = meta.mimeType;
            chip.dataset.size = meta.size;
            chip.innerHTML = '&#128196; ' + meta.fileName +
                ' <span class="remove-chip" onclick="this.parentElement.remove()">&#10005;</span>';
            editor.appendChild(chip);
            placeCaretAtEnd();
        }

        // --- Upload ---
        async function uploadFile(file) {
            const formData = new FormData();
            formData.append('file', file);
            try {
                const resp = await fetch('/api/chat/upload', { method: 'POST', body: formData });
                if (!resp.ok) {
                    const text = await resp.text();
                    alert('上传失败: ' + text);
                    return null;
                }
                return await resp.json();
            } catch (err) {
                alert('上传失败: ' + err.message);
                return null;
            }
        }

        function showUploading(name) {
            uploadStatus.textContent = '上传中: ' + name + '...';
        }

        function hideUploading() {
            uploadStatus.textContent = '';
        }

        // --- Serialization ---
        function serializeBlocks() {
            const blocks = [];
            let nextId = 1;

            function processNode(node) {
                if (node.nodeType === Node.TEXT_NODE) {
                    const text = node.textContent;
                    if (!text || text.trim() === '') return;
                    if (text.includes('\n')) {
                        const parts = text.split('\n');
                        for (const part of parts) {
                            const trimmed = part.trim();
                            if (trimmed) {
                                blocks.push({ id: nextId++, type: 'TEXT', text: trimmed });
                            }
                        }
                    } else {
                        const trimmed = text.trim();
                        if (trimmed) {
                            blocks.push({ id: nextId++, type: 'TEXT', text: trimmed });
                        }
                    }
                } else if (node.nodeType === Node.ELEMENT_NODE) {
                    const el = node;
                    if (el.tagName === 'IMG') {
                        if (el.dataset.inlineData) {
                            blocks.push({
                                id: nextId++,
                                type: 'IMAGE',
                                mimeType: el.dataset.mimeType,
                                inlineData: el.dataset.inlineData
                            });
                        } else if (el.dataset.fileId) {
                            blocks.push({
                                id: nextId++,
                                type: 'IMAGE',
                                fileId: el.dataset.fileId,
                                mimeType: el.dataset.mimeType
                            });
                        }
                    } else if (el.classList.contains('file-chip')) {
                        blocks.push({
                            id: nextId++,
                            type: 'FILE',
                            fileId: el.dataset.fileId,
                            fileName: el.dataset.fileName,
                            mimeType: el.dataset.mimeType,
                            size: parseInt(el.dataset.size, 10)
                        });
                    } else if (el.tagName === 'BR') {
                        // Ignore <br> — visual only
                    } else {
                        // <div>, <p>, <span> etc — recurse into children
                        for (const child of el.childNodes) {
                            processNode(child);
                        }
                    }
                }
            }

            for (const child of editor.childNodes) {
                processNode(child);
            }

            return blocks;
        }

        // --- Send ---
        async function sendMessage() {
            const blocks = serializeBlocks();
            if (blocks.length === 0) return;

            // Render user message
            renderUserMessage(blocks);

            // Clear editor
            editor.innerHTML = '';
            sendBtn.disabled = true;

            // Create assistant message container
            const assistantDiv = document.createElement('div');
            assistantDiv.className = 'message assistant';
            const cursor = document.createElement('span');
            cursor.className = 'cursor';
            assistantDiv.appendChild(cursor);
            messagesEl.appendChild(assistantDiv);
            scrollToBottom();

            try {
                const response = await fetch('/api/chat/stream/v3', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ blocks })
                });

                if (!response.ok) {
                    const errText = await response.text();
                    assistantDiv.textContent = '错误: ' + errText;
                    cursor.remove();
                    return;
                }

                const reader = response.body.getReader();
                const decoder = new TextDecoder();
                let fullText = '';

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;

                    const chunk = decoder.decode(value, { stream: true });
                    const lines = chunk.split('\n');
                    for (const line of lines) {
                        if (line.startsWith('data:')) {
                            fullText += line.substring(5);
                            assistantDiv.textContent = fullText;
                            assistantDiv.appendChild(cursor);
                            scrollToBottom();
                        }
                    }
                }

                cursor.remove();
            } catch (err) {
                assistantDiv.textContent = '发送失败: ' + err.message;
                cursor.remove();
            } finally {
                sendBtn.disabled = false;
                editor.focus();
            }
        }

        function renderUserMessage(blocks) {
            const div = document.createElement('div');
            div.className = 'message user';

            for (const block of blocks) {
                if (block.type === 'TEXT') {
                    const p = document.createElement('div');
                    p.textContent = block.text;
                    div.appendChild(p);
                } else if (block.type === 'IMAGE') {
                    const img = document.createElement('img');
                    if (block.inlineData) {
                        img.src = 'data:' + block.mimeType + ';base64,' + block.inlineData;
                    } else if (block.fileId) {
                        img.src = '/api/chat/files/' + block.fileId;
                    }
                    div.appendChild(img);
                } else if (block.type === 'FILE') {
                    const tag = document.createElement('div');
                    tag.className = 'file-tag';
                    tag.textContent = '📄 ' + block.fileName;
                    div.appendChild(tag);
                }
            }

            messagesEl.appendChild(div);
            scrollToBottom();
        }

        function scrollToBottom() {
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }

        function placeCaretAtEnd() {
            const range = document.createRange();
            range.selectNodeContents(editor);
            range.collapse(false);
            const sel = window.getSelection();
            sel.removeAllRanges();
            sel.addRange(range);
        }
    </script>
</body>
</html>
```

- [ ] **Step 2: Verify build succeeds**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run all tests**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw test -q`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/chat3.html
git commit -m "feat(block): add chat3.html rich text editor with block serialization"
```

---

## Task 9: Final Integration Verification

- [ ] **Step 1: Run full build**

Run: `cd /Users/zec/Repo/zeChat && ./mvnw clean package -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Manual smoke test checklist**

Start the app: `cd /Users/zec/Repo/zeChat && ./mvnw spring-boot:run`

Then open `http://localhost:8080/chat3.html` and verify:
1. Type text and send → AI responds via SSE stream
2. Paste a small image from clipboard → appears inline in editor → sends as inlineData
3. Attach a .txt file via button → file chip appears → sends as FileBlock → file content included in LLM context
4. Open `http://localhost:8080/chat.html` → original chat still works (backward compatible)

- [ ] **Step 3: Final commit**

If any fixes were needed during smoke test, commit them:

```bash
git add -A
git commit -m "fix(block): integration fixes from smoke testing"
```
