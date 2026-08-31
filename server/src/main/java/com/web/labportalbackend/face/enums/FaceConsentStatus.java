package com.web.labportalbackend.face.enums;

public enum FaceConsentStatus {
    GRANTED,
    WITHDRAWN,
    DELETE_REQUESTED,
    DELETED;

    public boolean allowsActiveProfile() {
        return this == GRANTED;
    }
}
