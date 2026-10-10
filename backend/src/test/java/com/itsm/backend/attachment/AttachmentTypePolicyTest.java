package com.itsm.backend.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import com.itsm.backend.common.error.ApiException;
import com.itsm.backend.common.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttachmentTypePolicyTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0};
    static final byte[] TEXT = "2026-10-11 ERROR Outlook crashed\n".getBytes(StandardCharsets.UTF_8);
    static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0};

    @Test
    void acceptsAllowedTypesAndDecidesTheContentType() {
        assertThat(AttachmentTypePolicy.check("screen.PNG", PNG))
                .isEqualTo(new AttachmentTypePolicy.Accepted("screen.PNG", "image/png"));
        assertThat(AttachmentTypePolicy.check("rapor.pdf", PDF).contentType()).isEqualTo("application/pdf");
        assertThat(AttachmentTypePolicy.check("outlook.log", TEXT).contentType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(AttachmentTypePolicy.check("ayarlar.docx", ZIP).contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(AttachmentTypePolicy.check("anim.gif", "GIF89a....".getBytes(StandardCharsets.US_ASCII)).contentType())
                .isEqualTo("image/gif");
        assertThat(AttachmentTypePolicy.check("foto.webp", "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))
                .contentType()).isEqualTo("image/webp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"setup.exe", "script.sh", "page.html", "logo.svg", "app.js", "noextension", "archive.tar.gz"})
    void rejectsTypesOutsideTheAllowList(String name) {
        assertCode(() -> AttachmentTypePolicy.check(name, TEXT), "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void rejectsContentThatDoesNotMatchTheExtension() {
        assertCode(() -> AttachmentTypePolicy.check("innocent.png", EXE), "UNSUPPORTED_FILE_TYPE");
        assertCode(() -> AttachmentTypePolicy.check("report.pdf", ZIP), "UNSUPPORTED_FILE_TYPE");
        assertCode(() -> AttachmentTypePolicy.check("notes.txt", EXE), "UNSUPPORTED_FILE_TYPE"); // contains NUL
    }

    @Test
    void rejectsEmptyFiles() {
        assertCode(() -> AttachmentTypePolicy.check("empty.txt", new byte[0]), "EMPTY_FILE");
    }

    @Test
    void keepsOnlyTheLastPathSegmentOfTheName() {
        assertThat(AttachmentTypePolicy.check("../../etc/passwd.txt", TEXT).fileName()).isEqualTo("passwd.txt");
        assertThat(AttachmentTypePolicy.check("C:\\Users\\ayse\\Desktop\\ekran görüntüsü.png", PNG).fileName())
                .isEqualTo("ekran görüntüsü.png");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "folder/", "..", "bad\u0000name.txt", "line\nbreak.txt"})
    void rejectsInvalidNames(String name) {
        assertCode(() -> AttachmentTypePolicy.check(name, TEXT), "INVALID_FILE_NAME");
    }

    @Test
    void shortensVeryLongNamesKeepingTheExtension() {
        String name = "a".repeat(300) + ".txt";

        String accepted = AttachmentTypePolicy.check(name, TEXT).fileName();

        assertThat(accepted).hasSize(255).endsWith(".txt");
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(code);
    }
}
