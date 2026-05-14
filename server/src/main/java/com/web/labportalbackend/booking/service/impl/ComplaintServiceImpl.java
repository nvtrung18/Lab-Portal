package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.request.ComplaintRequest;
import com.web.labportalbackend.booking.dto.response.ComplaintResponse;
import com.web.labportalbackend.booking.entity.ComplaintEntity;
import com.web.labportalbackend.booking.mapper.ComplaintMapper;
import com.web.labportalbackend.booking.repository.ComplaintRepository;
import com.web.labportalbackend.booking.service.ComplaintService;
import com.web.labportalbackend.common.enums.ComplaintStatus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ComplaintResponse submitComplaint(ComplaintRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + request.getUserId()));

        ComplaintEntity complaint = ComplaintEntity.builder()
                .user(user)
                .content(request.getContent())
                .status(ComplaintStatus.PENDING)
                .build();

        return ComplaintMapper.toResponse(complaintRepository.save(complaint));
    }
}
