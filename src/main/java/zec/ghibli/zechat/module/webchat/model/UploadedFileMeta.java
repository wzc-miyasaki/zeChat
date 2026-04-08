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
