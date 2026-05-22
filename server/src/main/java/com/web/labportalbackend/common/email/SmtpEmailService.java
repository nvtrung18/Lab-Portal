package com.web.labportalbackend.common.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class SmtpEmailService implements EmailService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from:}")
    private String from;

    public SmtpEmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        validateMailConfig();

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(resolveFromAddress());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Sent email to {} with subject {}", to, subject);
        } catch (MailException ex) {
            log.error("Could not send email to {} with subject {}: {}", to, subject, ex.getMessage(), ex);
            throw new IllegalStateException("Không thể gửi mã xác nhận. Vui lòng kiểm tra cấu hình email hoặc thử lại sau.");
        }
    }

    @Override
    public void sendRegisterOtp(String email, String code) {
        sendEmail(
                email,
                "Xác thực tài khoản hệ thống quản lý PTN",
                String.join("\n\n",
                        "Xin chào,",
                        "Mã xác thực tài khoản của bạn là: " + code,
                        "Mã có hiệu lực trong 10 phút.",
                        "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email."
                )
        );
    }

    @Override
    public void sendPasswordResetOtp(String email, String code) {
        sendEmail(
                email,
                "Xác nhận đặt lại mật khẩu",
                String.join("\n\n",
                        "Xin chào,",
                        "Mã xác nhận đặt lại mật khẩu của bạn là: " + code,
                        "Mã có hiệu lực trong 10 phút.",
                        "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email."
                )
        );
    }

    @Override
    public void sendBookingCreatedEmail(String email, BookingEmailData data) {
        sendEmail(email, "Đã ghi nhận đăng ký sử dụng PTN", String.join("\n",
                greeting(data),
                "Hệ thống đã ghi nhận đăng ký sử dụng PTN của bạn.",
                "PTN: " + data.getLabName(),
                "Thời gian: " + formatRange(data),
                "Trạng thái: Chờ phê duyệt",
                "Vui lòng chờ quản lý PTN phê duyệt."
        ));
    }

    @Override
    public void sendBookingApprovedEmail(String email, BookingEmailData data) {
        sendEmail(email, "Đăng ký sử dụng PTN đã được phê duyệt", String.join("\n",
                greeting(data),
                "Đăng ký sử dụng PTN của bạn đã được phê duyệt.",
                "PTN: " + data.getLabName(),
                "Thời gian: " + formatRange(data),
                "Trạng thái: Đã phê duyệt",
                "Khi đến giờ sử dụng, sinh viên vui lòng mở hệ thống để tạo mã QR check-in và đưa cho quản lý PTN quét xác nhận."
        ));
    }

    @Override
    public void sendBookingRejectedEmail(String email, BookingEmailData data) {
        sendEmail(email, "Đăng ký sử dụng PTN không được phê duyệt", String.join("\n",
                greeting(data),
                "Đăng ký sử dụng PTN của bạn không được phê duyệt.",
                "PTN: " + data.getLabName(),
                "Thời gian: " + formatRange(data),
                "Lý do: " + blankToDefault(data.getNote(), "Không có ghi chú")
        ));
    }

    @Override
    public void sendBookingCancelledByStudentEmail(String email, BookingEmailData data) {
        sendEmail(email, "Bạn đã hủy đăng ký sử dụng PTN", String.join("\n",
                greeting(data),
                "Bạn đã hủy đăng ký sử dụng PTN.",
                "PTN: " + data.getLabName(),
                "Thời gian: " + formatRange(data)
        ));
    }

    @Override
    public void sendSlotCancelledEmail(String email, SlotCancelledEmailData data) {
        String body = String.join("\n",
                "Khung giờ sử dụng PTN của bạn đã bị hủy.",
                "PTN: " + data.getLabName(),
                "Thời gian: " + DATE_TIME_FORMATTER.format(data.getStartTime()) + " - " + DATE_TIME_FORMATTER.format(data.getEndTime()),
                "Lý do: " + blankToDefault(data.getReason(), "Không có ghi chú"),
                "Người quản lý PTN: " + blankToDefault(data.getManagerName(), "Chưa cập nhật"),
                "Bạn có thể đăng ký khung giờ khác trên hệ thống."
        );
        sendEmail(email, "Thông báo hủy khung giờ sử dụng PTN", body);
    }

    private void validateMailConfig() {
        if (mailSender == null) {
            log.error("JavaMailSender is not configured. Check spring.mail.* / MAIL_* values.");
            throw new IllegalStateException("Không thể gửi email do hệ thống chưa được cấu hình SMTP.");
        }
        if (mailUsername == null || mailUsername.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình MAIL_USERNAME hoặc MAIL_PASSWORD cho SMTP.");
        }
    }

    private String resolveFromAddress() {
        return from == null || from.isBlank() ? mailUsername : from;
    }

    private String greeting(BookingEmailData data) {
        return "Xin chào " + blankToDefault(data.getStudentName(), "bạn") + ",";
    }

    private String formatRange(BookingEmailData data) {
        return DATE_TIME_FORMATTER.format(data.getStartTime()) + " - " + DATE_TIME_FORMATTER.format(data.getEndTime());
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
