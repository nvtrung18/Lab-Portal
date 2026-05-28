package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.SubmitProductRequest;
import com.web.labportalbackend.research.dto.response.ProductResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProductEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.ProductStatus;
import com.web.labportalbackend.research.enums.ProductType;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProductRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LogService logService;

    @InjectMocks
    private ProductServiceImpl productService;

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitProduct_uploadsFileAndCreatesVersionedSubmittedProduct() throws Exception {
        ReflectionTestUtils.setField(productService, "productStoragePath", tempDir.toString());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", null, List.of())
        );
        User student = new User();
        student.setId(5L);
        student.setUsername("student");
        student.addRole(new Role("STUDENT", "Student"));

        SubmitProductRequest request = new SubmitProductRequest();
        request.setProjectId(10L);
        request.setProductType(ProductType.SOURCE_CODE);
        request.setTitle("Source code demo nhận diện khuôn mặt");
        request.setDescription("Demo source");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "..\\final.zip",
                "application/zip",
                "artifact".getBytes()
        );

        ProjectEntity project = ProjectEntity.builder().title("Project").build();
        project.setId(10L);

        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(project));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(10L, 5L))
                .thenReturn(Optional.of(GroupRole.MEMBER));
        when(productRepository.findMaxPersonalVersionByProjectIdAndSubmitterAndProductType(
                10L,
                5L,
                ProductType.SOURCE_CODE
        )).thenReturn(Optional.of(1));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity product = invocation.getArgument(0);
            product.setId(100L);
            product.setCreatedAt(Instant.parse("2026-05-14T03:00:00Z"));
            return product;
        });

        ProductResponse response = productService.submitProduct(request, file);

        assertEquals(100L, response.getId());
        assertEquals(10L, response.getProjectId());
        assertEquals(ProductType.SOURCE_CODE, response.getProductType());
        assertEquals("Source code demo nhận diện khuôn mặt", response.getTitle());
        assertEquals(2, response.getVersion());
        assertEquals(ProductStatus.SUBMITTED, response.getStatus());
        assertEquals("/storage/products/10/2.zip", response.getFileUrl());
        assertTrue(Files.exists(tempDir.resolve("10").resolve("2.zip")));

        ArgumentCaptor<ProductEntity> captor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getProjectId());
        assertEquals(5L, captor.getValue().getSubmittedById());
        assertEquals("final.zip", captor.getValue().getFileName());
        verify(logService).logAction(10L, 5L, "SUBMIT_PRODUCT",
                "Submitted product: Source code demo nhận diện khuôn mặt v2");
    }

    @Test
    void submitProduct_rejectsMemberSubmittingGroupProduct() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("member", null, List.of())
        );
        User member = new User();
        member.setId(6L);
        member.setUsername("member");
        member.addRole(new Role("STUDENT", "Student"));

        SubmitProductRequest request = new SubmitProductRequest();
        request.setProjectId(10L);
        request.setGroupId(20L);
        request.setProductType(ProductType.PAPER);
        request.setTitle("Paper");
        request.setExternalLink("https://example.com/paper");

        ProjectEntity project = ProjectEntity.builder().title("Project").build();
        project.setId(10L);
        GroupEntity group = GroupEntity.builder().project(project).name("Group").leader(member).build();
        group.setId(20L);

        when(userRepository.findByUsername("member")).thenReturn(Optional.of(member));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(project));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(20L))
                .thenReturn(Optional.of(group));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(20L, 6L))
                .thenReturn(Optional.of(GroupRole.MEMBER));

        assertThrows(AccessDeniedException.class, () -> productService.submitProduct(request, null));
        verify(productRepository, never()).save(any(ProductEntity.class));
    }
}
