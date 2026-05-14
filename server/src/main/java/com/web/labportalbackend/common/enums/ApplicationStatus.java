package com.web.labportalbackend.common.enums;

/**
 * Application status for CV submissions to laboratories.
 */
public enum ApplicationStatus {
    PENDING,      // Newly submitted application awaiting review
    REVIEWING,    // Application is being reviewed by lab manager
    APPROVED,     // Application has been approved
    REJECTED      // Application has been rejected
}
