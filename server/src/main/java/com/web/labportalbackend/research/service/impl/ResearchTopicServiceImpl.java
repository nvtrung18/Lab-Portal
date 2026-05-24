package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.research.dto.request.CreateTopicRequest;
import com.web.labportalbackend.research.dto.response.TopicResponse;
import com.web.labportalbackend.research.entity.ResearchTopicEntity;
import com.web.labportalbackend.research.enums.TopicStatus;
import com.web.labportalbackend.research.mapper.TopicMapper;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ResearchTopicRepository;
import com.web.labportalbackend.research.service.ResearchTopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResearchTopicServiceImpl implements ResearchTopicService {

    private final ResearchTopicRepository topicRepository;
    private final GroupRepository groupRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TopicResponse createTopic(CreateTopicRequest request) {
        User currentUser = getCurrentUser();
        Laboratory lab = laboratoryRepository.findById(request.getLabId())
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", request.getLabId()));
        assertManagerOwnsLab(currentUser, lab);
        assertLabCanHostResearch(lab);

        ResearchTopicEntity topic = ResearchTopicEntity.builder()
                .lab(lab)
                .name(request.getName())
                .description(request.getDescription())
                .requirements(request.getRequirements())
                .references(request.getReferences())
                .manager(currentUser)
                .createdBy(currentUser)
                .status(request.getStatus() != null ? request.getStatus() : TopicStatus.RECRUITING)
                .build();

        return TopicMapper.toResponse(topicRepository.save(topic), 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicResponse> getByLab(Long labId) {
        Laboratory lab = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", labId));
        assertCanViewLab(getCurrentUser(), lab);

        return topicRepository.findByLabIdAndDeletedFalseAndActiveTrue(labId)
                .stream()
                .map(topic -> TopicMapper.toResponse(
                        topic,
                        groupRepository.countByTopicIdAndDeletedFalseAndActiveTrue(topic.getId())
                ))
                .toList();
    }

    private void assertCanViewLab(User currentUser, Laboratory lab) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerOwnsLab(currentUser, lab);
            return;
        }

        if (currentUser.hasRole("STUDENT") &&
                membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                        currentUser.getId(),
                        lab.getId()
                )) {
            return;
        }

        throw new AccessDeniedException("Cannot access research topics for this lab");
    }

    private void assertManagerOwnsLab(User currentUser, Laboratory lab) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can create research topics");
        }

        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (!managedLab.getId().equals(lab.getId())) {
            throw new AccessDeniedException("Cannot access research topics from another lab");
        }
    }

    private void assertLabCanHostResearch(Laboratory lab) {
        if (lab.getStatus() == LabStatus.INACTIVE || lab.getStatus() == LabStatus.CLOSED) {
            throw new IllegalStateException("Cannot create research topic in an inactive lab");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }
}
