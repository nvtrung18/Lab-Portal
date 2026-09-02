package com.web.labportalbackend.face.service;

import com.web.labportalbackend.face.dto.request.FaceCheckinRequest;
import com.web.labportalbackend.face.dto.request.FaceGuidanceRequest;
import com.web.labportalbackend.face.dto.response.FaceChallengeResponse;
import com.web.labportalbackend.face.dto.response.FaceCheckinResponse;
import com.web.labportalbackend.face.dto.response.FaceCheckinCandidateResponse;
import com.web.labportalbackend.face.dto.response.FaceGuidanceResponse;
import java.util.List;

public interface FaceCheckinService {
    List<FaceCheckinCandidateResponse> candidates();
    FaceGuidanceResponse guidance(FaceGuidanceRequest request);
    FaceChallengeResponse startPassiveSession();
    FaceCheckinResponse checkIn(FaceCheckinRequest request);
}
