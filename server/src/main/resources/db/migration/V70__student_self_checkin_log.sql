CREATE TABLE student_self_checkin_log (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    booking_id      BIGINT         NOT NULL,
    user_id         BIGINT         NOT NULL,
    lab_id          BIGINT         NOT NULL,
    client_ip       VARCHAR(100)   NOT NULL,
    latitude        DECIMAL(10, 7) NOT NULL,
    longitude       DECIMAL(10, 7) NOT NULL,
    accuracy_meters DECIMAL(12, 2) NOT NULL,
    distance_meters DECIMAL(12, 2) NOT NULL,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_student_self_checkin_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_student_self_checkin_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_student_self_checkin_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id),
    INDEX idx_student_self_checkin_booking_created (booking_id, created_at, id),
    INDEX idx_student_self_checkin_user_created (user_id, created_at, id),
    INDEX idx_student_self_checkin_lab_created (lab_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
