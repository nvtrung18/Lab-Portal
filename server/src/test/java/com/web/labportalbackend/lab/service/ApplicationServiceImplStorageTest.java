package com.web.labportalbackend.lab.service;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.ApplicationStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.lab.entity.Application;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.ApplicationRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.lab.service.impl.ApplicationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationServiceImplStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void cvUsesConfiguredDirectoryAndKeepsExistingPublicUrlContract() throws Exception {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class, Answers.RETURNS_DEFAULTS);
        ApplicationServiceImpl service = new ApplicationServiceImpl(
                applicationRepository, userRepository, laboratoryRepository, membershipRepository, auditLogService
        );
        ReflectionTestUtils.setField(service, "cvStoragePath", tempDir.resolve("custom-cv").toString());

        User user = new User();
        user.setId(7L);
        user.setFullName("Researcher");
        user.setEmail("researcher@example.invalid");
        Laboratory laboratory = new Laboratory();
        laboratory.setId(11L);
        laboratory.setLabName("Lab");
        laboratory.setStatus(LabStatus.AVAILABLE);
        Application saved = new Application();
        saved.setId(19L);
        saved.setUser(user);
        saved.setLaboratory(laboratory);
        saved.setStatus(ApplicationStatus.PENDING);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(laboratoryRepository.findById(11L)).thenReturn(Optional.of(laboratory));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(7L, 11L)).thenReturn(false);
        when(applicationRepository.existsByUserIdAndLaboratoryIdAndStatusAndDeletedFalse(7L, 11L, ApplicationStatus.PENDING)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> {
            Application persisted = invocation.getArgument(0);
            persisted.setId(saved.getId());
            return persisted;
        });

        var response = service.apply(11L, 7L, null,
                new MockMultipartFile("cvFile", "../CV résumé.pdf", "application/pdf", "cv".getBytes()));

        assertThat(response.getCvFileUrl()).startsWith("/api/uploads/cv/").doesNotContain("..", "\\");
        Path storedFile = Files.list(tempDir.resolve("custom-cv")).findFirst().orElseThrow();
        assertThat(Files.exists(storedFile)).isTrue();
    }
}
