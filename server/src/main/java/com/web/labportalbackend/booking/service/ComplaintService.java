package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.request.ComplaintRequest;
import com.web.labportalbackend.booking.dto.request.ReviewComplaintRequest;
import com.web.labportalbackend.booking.dto.response.ComplaintResponse;

import java.util.List;

public interface ComplaintService {
    ComplaintResponse submitComplaint(ComplaintRequest request);

    List<ComplaintResponse> getLabComplaints(Long labId);

    ComplaintResponse reviewComplaint(Long complaintId, ReviewComplaintRequest request);
}
