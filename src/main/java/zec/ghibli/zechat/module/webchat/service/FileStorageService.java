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
