package com.itsm.backend.attachment;

import java.io.IOException;
import java.io.InputStream;

/** Where attachment contents are kept. Files are addressed by a random key, never by the user's file name. */
public interface AttachmentStorage {

    /** A file written to storage. */
    record StoredFile(String key, long size, String sha256) {
    }

    StoredFile store(InputStream content) throws IOException;

    InputStream open(String key) throws IOException;

    /** Best effort: a file that cannot be removed is logged, not thrown. */
    void delete(String key);
}
