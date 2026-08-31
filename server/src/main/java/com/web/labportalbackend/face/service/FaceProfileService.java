package com.web.labportalbackend.face.service;

import com.web.labportalbackend.face.dto.request.FaceConsentRequest;
import com.web.labportalbackend.face.dto.request.FaceRegistrationRequest;
import com.web.labportalbackend.face.dto.response.FaceConsentResponse;
import com.web.labportalbackend.face.dto.response.FaceProfileResponse;

public interface FaceProfileService {
    FaceConsentResponse changeConsent(Long targetUserId, FaceConsentRequest request);
    FaceConsentResponse getConsent(Long targetUserId);
    FaceProfileResponse register(Long targetUserId, FaceRegistrationRequest request);
    FaceProfileResponse getProfile(Long targetUserId);
    void deleteProfile(Long targetUserId);
}
