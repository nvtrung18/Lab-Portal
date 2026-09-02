package com.web.labportalbackend.face.service;

import com.web.labportalbackend.face.dto.request.FaceConsentRequest;
import com.web.labportalbackend.face.dto.request.FaceGuidanceRequest;
import com.web.labportalbackend.face.dto.request.FaceRegistrationRequest;
import com.web.labportalbackend.face.dto.response.FaceConsentResponse;
import com.web.labportalbackend.face.dto.response.FaceChallengeResponse;
import com.web.labportalbackend.face.dto.response.FaceGuidanceResponse;
import com.web.labportalbackend.face.dto.response.FaceProfileResponse;
import java.util.List;

public interface FaceProfileService {
    List<FaceProfileResponse> listProfiles();
    FaceConsentResponse changeConsent(Long targetUserId, FaceConsentRequest request);
    FaceConsentResponse getConsent(Long targetUserId);
    FaceGuidanceResponse guidance(Long targetUserId, FaceGuidanceRequest request);
    FaceChallengeResponse startChallenge(Long targetUserId);
    FaceProfileResponse register(Long targetUserId, FaceRegistrationRequest request);
    FaceProfileResponse getProfile(Long targetUserId);
    void deleteProfile(Long targetUserId);
}
