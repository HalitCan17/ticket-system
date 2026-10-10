package com.itsm.backend.attachment;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import com.itsm.backend.common.error.BadRequestException;

/**
 * Which files may be attached. The extension must be on the allow list <em>and</em> the first bytes must
 * match that type, so a renamed executable is rejected. The content type served on download is decided
 * here, never taken from the client. See docs/adr/0008-attachments.md.
 */
public final class AttachmentTypePolicy {

    /** Bytes of the file start needed for the checks. */
    public static final int HEAD_SIZE = 8 * 1024;

    private record FileType(String contentType, Predicate<byte[]> matches) {
    }

    private static final Predicate<byte[]> ZIP = head ->
            startsWith(head, 0x50, 0x4B, 0x03, 0x04) || startsWith(head, 0x50, 0x4B, 0x05, 0x06);
    private static final Predicate<byte[]> TEXT = head -> {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    };

    private static final Map<String, FileType> TYPES = Map.ofEntries(
            Map.entry("png", new FileType("image/png", h -> startsWith(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))),
            Map.entry("jpg", new FileType("image/jpeg", h -> startsWith(h, 0xFF, 0xD8, 0xFF))),
            Map.entry("jpeg", new FileType("image/jpeg", h -> startsWith(h, 0xFF, 0xD8, 0xFF))),
            Map.entry("gif", new FileType("image/gif", h -> startsWithAscii(h, 0, "GIF87a") || startsWithAscii(h, 0, "GIF89a"))),
            Map.entry("webp", new FileType("image/webp", h -> startsWithAscii(h, 0, "RIFF") && startsWithAscii(h, 8, "WEBP"))),
            Map.entry("pdf", new FileType("application/pdf", h -> startsWithAscii(h, 0, "%PDF-"))),
            Map.entry("txt", new FileType("text/plain; charset=UTF-8", TEXT)),
            Map.entry("log", new FileType("text/plain; charset=UTF-8", TEXT)),
            Map.entry("csv", new FileType("text/csv; charset=UTF-8", TEXT)),
            Map.entry("zip", new FileType("application/zip", ZIP)),
            Map.entry("docx", new FileType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP)),
            Map.entry("xlsx", new FileType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ZIP)),
            Map.entry("pptx", new FileType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation", ZIP)));

    private AttachmentTypePolicy() {
    }

    /**
     * @param originalName name sent by the client (may contain a path)
     * @param head         first {@link #HEAD_SIZE} bytes of the file (fewer if the file is smaller)
     * @return the sanitized file name and the content type to serve it with
     * @throws BadRequestException {@code INVALID_FILE_NAME}, {@code EMPTY_FILE} or {@code UNSUPPORTED_FILE_TYPE}
     */
    public static Accepted check(String originalName, byte[] head) {
        String name = sanitizeName(originalName);
        if (head.length == 0) {
            throw new BadRequestException("EMPTY_FILE", "The file is empty.");
        }
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        FileType type = TYPES.get(extension);
        if (type == null) {
            throw new BadRequestException("UNSUPPORTED_FILE_TYPE",
                    "Files of this type cannot be attached. Allowed: " + String.join(", ", allowedExtensions()) + ".");
        }
        if (!type.matches().test(head)) {
            throw new BadRequestException("UNSUPPORTED_FILE_TYPE",
                    "The file content does not match its ." + extension + " extension.");
        }
        return new Accepted(name, type.contentType());
    }

    public static String[] allowedExtensions() {
        return TYPES.keySet().stream().sorted().toArray(String[]::new);
    }

    /** Last path segment, trimmed, without control characters, at most 255 characters. */
    static String sanitizeName(String originalName) {
        String name = originalName == null ? "" : originalName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.chars().anyMatch(Character::isISOControl)) {
            throw new BadRequestException("INVALID_FILE_NAME", "The file name is not valid.");
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    /** A file that passed the checks. */
    public record Accepted(String fileName, String contentType) {
    }

    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] head, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        return head.length >= offset + bytes.length
                && Arrays.equals(head, offset, offset + bytes.length, bytes, 0, bytes.length);
    }
}
