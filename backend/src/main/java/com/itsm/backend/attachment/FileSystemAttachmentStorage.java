package com.itsm.backend.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stores attachments as files named by a random UUID in {@code itsm.attachments.dir}. Content is written to
 * a temporary file first and moved into place only when complete, so a failed upload leaves no partial file.
 */
@Component
public class FileSystemAttachmentStorage implements AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(FileSystemAttachmentStorage.class);
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path dir;

    public FileSystemAttachmentStorage(AttachmentProperties properties) {
        this.dir = properties.dir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create attachment directory " + dir, e);
        }
    }

    @Override
    public StoredFile store(InputStream content) throws IOException {
        Path temp = Files.createTempFile(dir, "upload-", ".tmp");
        try {
            MessageDigest sha256 = sha256();
            long size;
            try (DigestInputStream in = new DigestInputStream(content, sha256)) {
                size = Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            String key = UUID.randomUUID().toString();
            Files.move(temp, dir.resolve(key), StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(key, size, HexFormat.of().formatHex(sha256.digest()));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Could not delete attachment file {}", key, e);
        }
    }

    /** Keys are UUIDs we generated; anything else is rejected so no path can escape the directory. */
    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return dir.resolve(key);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
