package com.web.labportalbackend.face.service;

import com.web.labportalbackend.face.dto.request.FaceCheckinRequest;
import com.web.labportalbackend.face.dto.response.FaceCheckinResponse;

public interface FaceCheckinService {
    FaceCheckinResponse checkIn(FaceCheckinRequest request);
}
