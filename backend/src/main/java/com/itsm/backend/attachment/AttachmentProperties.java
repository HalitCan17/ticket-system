package com.itsm.backend.attachment;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param dir         directory for attachment contents (a Docker volume in Compose)
 * @param maxFileSize largest accepted file; keep spring.servlet.multipart.max-file-size and nginx in line
 */
@ConfigurationProperties("itsm.attachments")
public record AttachmentProperties(
        @DefaultValue("data/attachments") Path dir,
        @DefaultValue("10MB") DataSize maxFileSize) {
}
