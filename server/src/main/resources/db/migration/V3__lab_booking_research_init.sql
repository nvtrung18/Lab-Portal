-- ============================================================
-- V3__lab_booking_research_init.sql
-- Lab, Booking, Research modules schema
-- ============================================================

-- -----------------------------------------------------------
-- 1. Laboratories table
-- -----------------------------------------------------------
CREATE TABLE laboratories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    lab_name    VARCHAR(100) NOT NULL,
    description TEXT         NULL,
    location    VARCHAR(200) NOT NULL,
    capacity    INT          NOT NULL,
    department  VARCHAR(100) NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_name UNIQUE (lab_name),
    INDEX idx_lab_name       (lab_name),
    INDEX idx_lab_department (department),
    INDEX idx_lab_status     (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 2. Bookings table
-- -----------------------------------------------------------
CREATE TABLE bookings (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    lab_id             BIGINT       NOT NULL,
    start_time         TIMESTAMP(6) NOT NULL,
    end_time           TIMESTAMP(6) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    purpose            TEXT         NULL,
    participants_count INT          NOT NULL DEFAULT 1,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_booking_lab  FOREIGN KEY (lab_id)  REFERENCES laboratories (id),
    INDEX idx_booking_user       (user_id),
    INDEX idx_booking_lab        (lab_id),
    INDEX idx_booking_status     (status),
    INDEX idx_booking_time_range (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 3. Research Projects table
-- -----------------------------------------------------------
CREATE TABLE research_projects (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    project_name VARCHAR(200) NOT NULL,
    description  TEXT         NULL,
    lab_id       BIGINT       NOT NULL,
    leader_id    BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PLANNING',
    domain       VARCHAR(100) NULL,
    team_size    INT          NOT NULL DEFAULT 1,
    objectives   TEXT         NULL,
    start_date   DATE         NULL,
    end_date     DATE         NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_project_name      UNIQUE (project_name),
    CONSTRAINT fk_research_lab      FOREIGN KEY (lab_id)    REFERENCES laboratories (id),
    CONSTRAINT fk_research_leader   FOREIGN KEY (leader_id) REFERENCES users (id),
    INDEX idx_research_name   (project_name),
    INDEX idx_research_lab    (lab_id),
    INDEX idx_research_leader (leader_id),
    INDEX idx_research_status (status),
    INDEX idx_research_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
