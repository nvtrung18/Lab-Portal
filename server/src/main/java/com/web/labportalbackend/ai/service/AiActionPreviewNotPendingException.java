package com.web.labportalbackend.ai.service;

/** Preview đã có kết quả cuối; không được thực hiện lại thao tác. */
public final class AiActionPreviewNotPendingException extends IllegalStateException {
    public AiActionPreviewNotPendingException() {
        super("AI action preview is no longer pending");
    }
}
