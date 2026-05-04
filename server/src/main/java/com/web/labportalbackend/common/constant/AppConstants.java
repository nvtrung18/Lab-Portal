package com.web.labportalbackend.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Application-wide constants.
 * Use enums (in common.enums) for status/role values.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AppConstants {

    // --- API versioning ---
    public static final String API_V1 = "/v1";

    // --- Pagination defaults ---
    public static final int DEFAULT_PAGE_NUMBER = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // --- Validation ---
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
}
