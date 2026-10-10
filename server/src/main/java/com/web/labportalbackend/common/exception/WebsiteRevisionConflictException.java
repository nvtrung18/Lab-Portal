package com.web.labportalbackend.common.exception;

public class WebsiteRevisionConflictException extends RuntimeException {
    public WebsiteRevisionConflictException() {
        super("Nội dung website đã được cập nhật ở phiên khác. Vui lòng tải lại trước khi lưu.");
    }
}
