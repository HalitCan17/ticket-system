package com.itsm.backend.kb;

/** Who can read an article: everyone, staff only, or staff only while it is still being written. */
public enum KbVisibility {
    DRAFT, INTERNAL, PUBLIC
}
