package com.web.labportalbackend.common.email;

public interface EmailService {
    void sendEmail(String to, String subject, String body);
    void sendRegisterOtp(String email, String code);
    void sendPasswordResetOtp(String email, String code);
    void sendBookingCreatedEmail(String email, BookingEmailData data);
    void sendBookingApprovedEmail(String email, BookingEmailData data);
    void sendBookingRejectedEmail(String email, BookingEmailData data);
    void sendBookingCancelledByStudentEmail(String email, BookingEmailData data);
    void sendBookingNoShowEmail(String email, BookingEmailData data);
    void sendSlotCancelledEmail(String email, BookingEmailData data);
    void sendBookingSessionCompletedEmail(String email, BookingEmailData data);
    void sendBookingCheckedInEmail(String email, BookingEmailData data);
    void sendPenaltyCreatedEmail(String email, BookingEmailData data);
    void sendSlotCancelledEmail(String email, SlotCancelledEmailData data);
}
