package com.itsm.backend.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttachmentControllerTest {

    @Test
    void contentDispositionHasAsciiFallbackAndUtf8Name() {
        assertThat(AttachmentController.contentDisposition("outlook.log"))
                .isEqualTo("attachment; filename=\"outlook.log\"; filename*=UTF-8''outlook.log");
        assertThat(AttachmentController.contentDisposition("Rapor \"son\" *şubat*.pdf"))
                .isEqualTo("attachment; filename=\"Rapor _son_ *_ubat*.pdf\"; "
                        + "filename*=UTF-8''Rapor%20%22son%22%20%2A%C5%9Fubat%2A.pdf");
    }
}
