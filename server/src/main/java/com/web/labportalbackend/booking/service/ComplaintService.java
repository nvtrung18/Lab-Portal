package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.request.ComplaintRequest;
import com.web.labportalbackend.booking.dto.response.ComplaintResponse;

public interface ComplaintService {
    ComplaintResponse submitComplaint(ComplaintRequest request);
}
