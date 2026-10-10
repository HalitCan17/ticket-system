package com.itsm.backend.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class FileSystemAttachmentStorageTest {

    @TempDir
    Path dir;

    @Test
    void storesUnderRandomKeyWithSizeAndHash() throws IOException {
        FileSystemAttachmentStorage storage = storage();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        AttachmentStorage.StoredFile stored = storage.store(new ByteArrayInputStream(content));

        assertThat(stored.key()).matches("[0-9a-f-]{36}");
        assertThat(stored.size()).isEqualTo(5);
        // sha256("hello")
        assertThat(stored.sha256()).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        try (InputStream in = storage.open(stored.key())) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
        assertThat(Files.list(dir)).containsExactly(dir.resolve(stored.key())); // no temp file left
    }

    @Test
    void deleteRemovesTheFileAndToleratesMissingOnes() throws IOException {
        FileSystemAttachmentStorage storage = storage();
        String key = storage.store(new ByteArrayInputStream(new byte[] {1})).key();

        storage.delete(key);
        storage.delete(key);

        assertThat(Files.list(dir)).isEmpty();
    }

    @Test
    void failedUploadLeavesNoFile() {
        FileSystemAttachmentStorage storage = storage();
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };

        assertThatThrownBy(() -> storage.store(broken)).isInstanceOf(IOException.class);
        assertThat(dir.toFile().list()).isEmpty();
    }

    @Test
    void rejectsKeysThatCouldEscapeTheDirectory() {
        FileSystemAttachmentStorage storage = storage();

        assertThatThrownBy(() -> storage.open("../application.yml")).isInstanceOf(IllegalArgumentException.class);
    }

    private FileSystemAttachmentStorage storage() {
        return new FileSystemAttachmentStorage(new AttachmentProperties(dir, DataSize.ofMegabytes(10)));
    }
}
