package com.web.labportalbackend.lab.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.lab.dto.response.ApplicationResponseDTO;
import com.web.labportalbackend.common.enums.ApplicationStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.exception.ApplicationAlreadyReviewedException;
import com.web.labportalbackend.common.exception.DuplicateApplicationException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Application;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.entity.Membership;
import com.web.labportalbackend.lab.repository.ApplicationRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.lab.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {
    private static final long MAX_CV_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_CV_EXTENSIONS = Set.of("pdf", "doc", "docx");

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final AuditLogService auditLogService;

    @Value("${app.storage.cv-path:uploads/cv}")
    private String cvStoragePath;

    @Override @Transactional
    public ApplicationResponseDTO apply(Long labId, Long userId, String cvUrl, MultipartFile cvFile) {
        log.info("Processing application: userId={}, labId={}", userId, labId);
        String normalizedCvUrl = normalizeCvUrl(cvUrl);
        boolean hasCvFile = cvFile != null && !cvFile.isEmpty();

        if ((normalizedCvUrl == null || normalizedCvUrl.isBlank()) && !hasCvFile) {
            throw new IllegalArgumentException("CV URL hoặc CV file là bắt buộc.");
        }
        if (normalizedCvUrl != null) {
            validateCvUrl(normalizedCvUrl);
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Laboratory lab = laboratoryRepository.findById(labId).orElseThrow(() -> new ResourceNotFoundException("Laboratory", labId));
        if (lab.getStatus() != LabStatus.AVAILABLE) {
            throw new IllegalArgumentException("Lab is inactive and cannot receive applications.");
        }
        if (membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(userId, labId)) {
            throw new DuplicateApplicationException("User is already an active member of this laboratory.");
        }
        if (applicationRepository.existsByUserIdAndLaboratoryIdAndStatusAndDeletedFalse(userId, labId, ApplicationStatus.PENDING)) {
            throw new DuplicateApplicationException("User already has a pending application for this laboratory.");
        }
        StoredCvFile storedCvFile = hasCvFile ? storeCvFile(cvFile) : null;
        Application app = new Application();
        app.setUser(user);
        app.setLaboratory(lab);
        app.setCvUrl(normalizedCvUrl);
        if (storedCvFile != null) {
            app.setCvFileUrl(storedCvFile.url());
            app.setCvFileName(storedCvFile.originalFileName());
            app.setCvContentType(storedCvFile.contentType());
            app.setCvSize(storedCvFile.size());
        }
        Application saved = applicationRepository.save(app);
        log.info("Application created: id={}", saved.getId());
        return mapToDTO(saved);
    }

    @Override @Transactional
    public ApplicationResponseDTO review(Long applicationId, ApplicationStatus newStatus) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        if (app.getStatus() != ApplicationStatus.PENDING) throw new ApplicationAlreadyReviewedException(applicationId);
        app.setStatus(newStatus);
        Application updated = applicationRepository.save(app);
        if (newStatus == ApplicationStatus.APPROVED) {
            Membership membership = membershipRepository
                    .findByUserIdAndLaboratoryIdAndDeletedFalse(app.getUser().getId(), app.getLaboratory().getId())
                    .orElseGet(() -> {
                        Membership created = new Membership();
                        created.setUser(app.getUser());
                        created.setLaboratory(app.getLaboratory());
                        created.setRole("MEMBER");
                        return created;
                    });
            membership.setActive(true);
            membershipRepository.save(membership);
            log.info("Membership activated: userId={}, labId={}", app.getUser().getId(), app.getLaboratory().getId());
        }
        auditLogService.logCurrentUser(
                AuditAction.REVIEW_APPLICATION,
                AuditModule.LAB,
                "APPLICATION",
                updated.getId(),
                "Manager đã duyệt hồ sơ ứng tuyển của " + displayName(app.getUser())
                        + " vào " + app.getLaboratory().getLabName() + " với kết quả " + newStatus + "."
        );
        return mapToDTO(updated);
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName();
    }

    @Override @Transactional(readOnly = true)
    public Page<ApplicationResponseDTO> getApplications(Pageable pageable) {
        return applicationRepository.findByDeletedFalse(pageable).map(this::mapToDTO);
    }

    @Override @Transactional(readOnly = true)
    public ApplicationResponseDTO getApplicationById(Long applicationId) {
        return mapToDTO(applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId)));
    }

    @Override @Transactional(readOnly = true)
    public Page<ApplicationResponseDTO> getApplicationsByUserId(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) throw new ResourceNotFoundException("User", userId);
        return toPage(applicationRepository.findByUserIdAndDeletedFalse(userId).stream().map(this::mapToDTO).toList(), pageable);
    }

    @Override @Transactional(readOnly = true)
    public Page<ApplicationResponseDTO> getApplicationsByLabId(Long labId, Pageable pageable) {
        if (!laboratoryRepository.existsById(labId)) throw new ResourceNotFoundException("Laboratory", labId);
        return toPage(applicationRepository.findByLaboratoryIdAndDeletedFalse(labId).stream().map(this::mapToDTO).toList(), pageable);
    }

    private ApplicationResponseDTO mapToDTO(Application app) {
        return ApplicationResponseDTO.builder()
                .id(app.getId()).userId(app.getUser().getId())
                .applicantName(app.getUser().getFullName())
                .applicantEmail(app.getUser().getEmail())
                .labId(app.getLaboratory().getId())
                .labName(app.getLaboratory().getLabName())
                .cvUrl(app.getCvUrl())
                .cvFileUrl(app.getCvFileUrl())
                .cvFileName(app.getCvFileName())
                .cvContentType(app.getCvContentType())
                .cvSize(app.getCvSize())
                .status(app.getStatus())
                .createdAt(app.getCreatedAt()).updatedAt(app.getUpdatedAt()).build();
    }

    private String normalizeCvUrl(String cvUrl) {
        if (cvUrl == null || cvUrl.isBlank()) {
            return null;
        }
        return cvUrl.trim();
    }

    private void validateCvUrl(String cvUrl) {
        try {
            URI uri = URI.create(cvUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException("CV URL không hợp lệ.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("CV URL không hợp lệ.");
        }
    }

    private StoredCvFile storeCvFile(MultipartFile cvFile) {
        validateCvFile(cvFile);

        String originalFileName = Paths.get(cvFile.getOriginalFilename() == null ? "cv" : cvFile.getOriginalFilename())
                .getFileName()
                .toString();
        String extension = getExtension(originalFileName);
        String safeOriginalName = originalFileName.replaceAll("[^A-Za-z0-9._-]", "_");
        String storedFileName = UUID.randomUUID() + "_" + safeOriginalName;
        Path uploadDirectory = Paths.get(cvStoragePath).toAbsolutePath().normalize();
        Path target = uploadDirectory.resolve(storedFileName).normalize();
        if (!target.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException("Invalid CV storage path");
        }

        try {
            Files.createDirectories(uploadDirectory);
            cvFile.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot store CV file.", ex);
        }

        return new StoredCvFile(
                "/api/uploads/cv/" + storedFileName,
                originalFileName,
                cvFile.getContentType(),
                cvFile.getSize(),
                extension);
    }

    private void validateCvFile(MultipartFile cvFile) {
        if (cvFile == null || cvFile.isEmpty()) {
            throw new IllegalArgumentException("CV file không hợp lệ.");
        }
        if (cvFile.getSize() > MAX_CV_FILE_SIZE) {
            throw new IllegalArgumentException("CV file không được vượt quá 10MB.");
        }

        String extension = getExtension(cvFile.getOriginalFilename());
        if (!ALLOWED_CV_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("CV file chỉ hỗ trợ định dạng pdf, doc hoặc docx.");
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private record StoredCvFile(
            String url,
            String originalFileName,
            String contentType,
            Long size,
            String extension) {
    }

    private <T> Page<T> toPage(List<T> list, Pageable pageable) {
        int from = (int) pageable.getOffset();
        int to = Math.min(from + pageable.getPageSize(), list.size());
        return new PageImpl<>(from <= list.size() ? list.subList(from, to) : Collections.emptyList(), pageable, list.size());
    }
}
