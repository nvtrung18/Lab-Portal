package com.web.labportalbackend.lab.service;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.config.SecurityConfig;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
import com.web.labportalbackend.common.config.StaticResourceConfig;
import com.web.labportalbackend.common.enums.ApplicationStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.lab.entity.Application;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.ApplicationRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.lab.service.impl.ApplicationServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ApplicationServiceImplStorageTest.EmptyController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(StaticResourceConfig.class)
class ApplicationServiceImplStorageTest {

    private static final Path TEMP_ROOT = createTempRoot();
    private static final Path CV_STORAGE_PATH = TEMP_ROOT.resolve("custom-cv");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.cv-path", CV_STORAGE_PATH::toString);
    }

    @AfterAll
    static void cleanup() throws IOException {
        FileSystemUtils.deleteRecursively(TEMP_ROOT);
    }

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
        ReflectionTestUtils.setField(service, "cvStoragePath", CV_STORAGE_PATH.toString());

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
        Path storedFile = Files.list(CV_STORAGE_PATH).findFirst().orElseThrow();
        assertThat(Files.exists(storedFile)).isTrue();
        mockMvc.perform(get(response.getCvFileUrl().substring("/api".length())))
                .andExpect(status().isOk())
                .andExpect(content().bytes("cv".getBytes()));
    }

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("lab-portal-cv-storage-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @RestController
    static class EmptyController {
    }
}
