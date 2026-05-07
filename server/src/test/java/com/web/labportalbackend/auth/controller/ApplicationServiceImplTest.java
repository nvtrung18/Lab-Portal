package com.web.labportalbackend.auth.controller;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.service.ApplicationServiceImpl;
import com.web.labportalbackend.common.dto.ApplicationResponseDTO;
import com.web.labportalbackend.common.enums.ApplicationStatus;
import com.web.labportalbackend.common.exception.ApplicationAlreadyReviewedException;
import com.web.labportalbackend.common.exception.DuplicateApplicationException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Application;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.entity.Membership;
import com.web.labportalbackend.lab.repository.ApplicationRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApplicationService.
 * Tests cover: application submission, duplicate prevention, review (approve/reject), and listing scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationService Unit Tests")
class ApplicationServiceImplTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    private User testUser;
    private Laboratory testLab;
    private Application testApplication;

    @BeforeEach
    void setUp() {
        // Setup test data
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@test.com");
        testUser.setUsername("testuser");
        testUser.setFullName("Test User");

        testLab = new Laboratory();
        testLab.setId(1L);
        testLab.setLabName("Physics Lab");
        testLab.setDescription("Physics Laboratory");
        testLab.setLocation("Building A");
        testLab.setCapacity(30);

        testApplication = new Application();
        testApplication.setId(1L);
        testApplication.setUser(testUser);
        testApplication.setLaboratory(testLab);
        testApplication.setCvUrl("https://example.com/cv.pdf");
        testApplication.setStatus(ApplicationStatus.PENDING);
        testApplication.setCreatedAt(Instant.now());
        testApplication.setUpdatedAt(Instant.now());
        testApplication.setActive(true);
        testApplication.setDeleted(false);
    }

    @Test
    @DisplayName("Apply - Should successfully create application when user and lab exist")
    void testApplySuccess() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(testLab));
        when(applicationRepository.existsByUserIdAndLaboratoryIdAndDeletedFalse(1L, 1L)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenReturn(testApplication);

        // Act
        ApplicationResponseDTO result = applicationService.apply(1L, 1L, "https://example.com/cv.pdf");

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals(1L, result.getLabId());
        assertEquals("https://example.com/cv.pdf", result.getCvUrl());
        assertEquals(ApplicationStatus.PENDING, result.getStatus());
        verify(applicationRepository, times(1)).save(any(Application.class));
    }

    @Test
    @DisplayName("Apply - Should throw ResourceNotFoundException when user not found")
    void testApplyUserNotFound() {
        // Arrange
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
                applicationService.apply(1L, 999L, "https://example.com/cv.pdf"));
        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Apply - Should throw ResourceNotFoundException when lab not found")
    void testApplyLabNotFound() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(laboratoryRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
                applicationService.apply(999L, 1L, "https://example.com/cv.pdf"));
        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Apply - Should throw DuplicateApplicationException when user already applied")
    void testApplyDuplicate() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(testLab));
        when(applicationRepository.existsByUserIdAndLaboratoryIdAndDeletedFalse(1L, 1L)).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateApplicationException.class, () -> 
                applicationService.apply(1L, 1L, "https://example.com/cv.pdf"));
        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("GetApplications - Should return paginated applications")
    void testGetApplications() {
        // Arrange
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        Page<Application> applicationPage = new PageImpl<>(List.of(testApplication), pageable, 1);
        when(applicationRepository.findByDeletedFalse(pageable)).thenReturn(applicationPage);

        // Act
        Page<ApplicationResponseDTO> result = applicationService.getApplications(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("Physics Lab", result.getContent().get(0).getLabName());
        verify(applicationRepository, times(1)).findByDeletedFalse(pageable);
    }

    @Test
    @DisplayName("GetApplications - Should return empty page when no applications exist")
    void testGetApplicationsEmpty() {
        // Arrange
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        Page<Application> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(applicationRepository.findByDeletedFalse(pageable)).thenReturn(emptyPage);

        // Act
        Page<ApplicationResponseDTO> result = applicationService.getApplications(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("GetApplicationById - Should return application when found")
    void testGetApplicationByIdSuccess() {
        // Arrange
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(testApplication));

        // Act
        ApplicationResponseDTO result = applicationService.getApplicationById(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Physics Lab", result.getLabName());
        verify(applicationRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("GetApplicationById - Should throw ResourceNotFoundException when not found")
    void testGetApplicationByIdNotFound() {
        // Arrange
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
                applicationService.getApplicationById(999L));
    }

    @Test
    @DisplayName("GetApplicationsByUserId - Should throw ResourceNotFoundException when user not found")
    void testGetApplicationsByUserIdUserNotFound() {
        // Arrange
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(userRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
                applicationService.getApplicationsByUserId(999L, pageable));
    }

    @Test
    @DisplayName("GetApplicationsByLabId - Should throw ResourceNotFoundException when lab not found")
    void testGetApplicationsByLabIdLabNotFound() {
        // Arrange
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(laboratoryRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
                applicationService.getApplicationsByLabId(999L, pageable));
    }

    @Test
    @DisplayName("Review - Should approve application and create membership")
    void testReviewApprove() {
        // Arrange
        testApplication.setStatus(ApplicationStatus.PENDING);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(testApplication));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndDeletedFalse(1L, 1L)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenReturn(testApplication);
        when(membershipRepository.save(any())).thenReturn(new com.web.labportalbackend.lab.entity.Membership());

        // Act
        ApplicationResponseDTO result = applicationService.review(1L, ApplicationStatus.APPROVED);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        // Verify membership save was called once
        verify(membershipRepository, times(1)).save(any());
        // Verify application save was called once
        verify(applicationRepository, times(1)).save(any(Application.class));
    }

    @Test
    @DisplayName("Review - Should reject application without creating membership")
    void testReviewReject() {
        // Arrange
        testApplication.setStatus(ApplicationStatus.PENDING);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(testApplication));
        when(applicationRepository.save(any(Application.class))).thenReturn(testApplication);

        // Act
        ApplicationResponseDTO result = applicationService.review(1L, ApplicationStatus.REJECTED);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        // Verify membership save was NOT called
        verify(membershipRepository, never()).save(any());
        // Verify application save was called once
        verify(applicationRepository, times(1)).save(any(Application.class));
    }

    @Test
    @DisplayName("Review - Should throw exception when application not found")
    void testReviewApplicationNotFound() {
        // Arrange
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
                applicationService.review(999L, ApplicationStatus.APPROVED));
        // Verify no save operations were called
        verify(applicationRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("Review - Should throw exception when application already reviewed")
    void testReviewAlreadyReviewed() {
        // Arrange
        testApplication.setStatus(ApplicationStatus.APPROVED);  // Already reviewed
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(testApplication));

        // Act & Assert
        assertThrows(ApplicationAlreadyReviewedException.class, () -> 
                applicationService.review(1L, ApplicationStatus.APPROVED));
        // Verify no save operations were called
        verify(applicationRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }
}
