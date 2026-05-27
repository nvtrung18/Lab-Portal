package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.SubmitProductRequest;
import com.web.labportalbackend.research.dto.response.ProductResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProductEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.ProductStatus;
import com.web.labportalbackend.research.enums.ProductType;
import com.web.labportalbackend.research.mapper.ProductMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProductRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.LogService;
import com.web.labportalbackend.research.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final long MAX_PRODUCT_FILE_SIZE = 50L * 1024 * 1024;
    private static final Map<ProductType, Set<String>> ALLOWED_EXTENSIONS = Map.of(
            ProductType.FINAL_REPORT, Set.of("pdf", "doc", "docx"),
            ProductType.SLIDE, Set.of("pdf", "ppt", "pptx"),
            ProductType.SOURCE_CODE, Set.of("zip"),
            ProductType.DATASET, Set.of("zip", "csv", "xlsx"),
            ProductType.DEMO_VIDEO, Set.of("mp4"),
            ProductType.PAPER, Set.of("pdf", "doc", "docx"),
            ProductType.SOFTWARE_DEMO, Set.of("zip", "mp4"),
            ProductType.OTHER, Set.of("pdf", "doc", "docx", "ppt", "pptx", "zip", "mp4", "csv", "xlsx")
    );

    private final ProductRepository productRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;
    private final LogService logService;

    @Value("${app.research.product-storage-path:storage/products}")
    private String productStoragePath;

    @Override
    @Transactional
    public ProductResponse submitProduct(SubmitProductRequest request, MultipartFile file) {
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only research students may submit products");
        }
        validateSubmitRequest(request, file);

        ProjectEntity project = findProject(request.getProjectId());
        Long groupId = resolveAndAuthorizeUploadGroupId(project, request.getGroupId(), currentUser);
        Integer version = nextVersion(project.getId(), groupId, currentUser.getId(), request.getProductType());
        StoredProductFile storedFile = file == null || file.isEmpty()
                ? null
                : storeProductFile(file, project.getId(), groupId, version, request.getProductType());

        ProductEntity product = ProductEntity.builder()
                .projectId(project.getId())
                .groupId(groupId)
                .submittedById(currentUser.getId())
                .productType(request.getProductType())
                .title(request.getTitle().trim())
                .description(trimToNull(request.getDescription()))
                .name(request.getTitle().trim())
                .fileUrl(storedFile == null ? null : storedFile.url())
                .fileName(storedFile == null ? null : storedFile.originalFileName())
                .fileType(storedFile == null ? null : storedFile.contentType())
                .fileSize(storedFile == null ? null : storedFile.size())
                .externalLink(normalizeExternalLink(request.getExternalLink()))
                .version(version)
                .status(ProductStatus.SUBMITTED)
                .submittedAt(Instant.now())
                .build();

        ProductEntity saved = productRepository.save(product);
        logService.logAction(project.getId(), currentUser.getId(), "SUBMIT_PRODUCT",
                "Submitted product: " + saved.getTitle() + " v" + saved.getVersion());
        return ProductMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getByProject(Long projectId) {
        ProjectEntity project = findProject(projectId);
        User currentUser = getCurrentUser();
        if (canManagerViewProjectProducts(project, currentUser)) {
            return productRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDescVersionDesc(projectId)
                    .stream()
                    .map(ProductMapper::toResponse)
                    .toList();
        }
        assertStudentCanViewProjectProducts(project, currentUser);
        List<Long> allowedGroupIds = groupMemberRepository
                .findActiveGroupIdsByProjectIdAndUserId(project.getId(), currentUser.getId());
        return productRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDescVersionDesc(projectId)
                .stream()
                .filter(product -> canStudentViewProduct(product, currentUser.getId(), allowedGroupIds))
                .map(ProductMapper::toResponse)
                .toList();
    }

    private void validateSubmitRequest(SubmitProductRequest request, MultipartFile file) {
        if (!StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("Title is required");
        }
        if (request.getProductType() == null) {
            throw new IllegalArgumentException("Product type is required");
        }
        if ((file == null || file.isEmpty()) && !StringUtils.hasText(request.getExternalLink())) {
            throw new IllegalArgumentException("File or external link is required");
        }
        if (file != null && !file.isEmpty()) {
            validateProductFile(file, request.getProductType());
        }
        if (StringUtils.hasText(request.getExternalLink())) {
            normalizeExternalLink(request.getExternalLink());
        }
    }

    private Long resolveAndAuthorizeUploadGroupId(ProjectEntity project, Long requestedGroupId, User currentUser) {
        if (requestedGroupId != null) {
            GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(requestedGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("Research group", requestedGroupId));
            if (!belongsToProject(group, project)) {
                throw new AccessDeniedException("Cannot submit products to a group outside this project");
            }
            groupMemberRepository.findActiveRoleByGroupIdAndUserId(group.getId(), currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Cannot submit products for this group"));
            return group.getId();
        }

        GroupRole role = groupMemberRepository
                .findActiveRoleByProjectIdAndUserId(project.getId(), currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Cannot submit products for this project"));
        if (role == GroupRole.LEADER && project.getGroup() != null) {
            return project.getGroup().getId();
        }
        return null;
    }

    private boolean canManagerViewProjectProducts(ProjectEntity project, User currentUser) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
            if (project.getLab() != null && managedLab.getId().equals(project.getLab().getId())) {
                return true;
            }
            throw new AccessDeniedException("Cannot access products from another lab");
        }
        return false;
    }

    private void assertStudentCanViewProjectProducts(ProjectEntity project, User currentUser) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Cannot access research products");
        }
        if (!groupMemberRepository.existsActiveMemberByProjectIdAndUserId(project.getId(), currentUser.getId())) {
            throw new AccessDeniedException("Cannot access products for this project");
        }
    }

    private boolean canStudentViewProduct(ProductEntity product, Long userId, List<Long> allowedGroupIds) {
        if (product.getGroupId() != null) {
            return allowedGroupIds.contains(product.getGroupId());
        }
        return userId.equals(product.getSubmittedById());
    }

    private boolean belongsToProject(GroupEntity group, ProjectEntity project) {
        boolean groupPointsToProject = group.getProject() != null && project.getId().equals(group.getProject().getId());
        boolean projectPointsToGroup = project.getGroup() != null && group.getId().equals(project.getGroup().getId());
        return groupPointsToProject || projectPointsToGroup;
    }

    private Integer nextVersion(Long projectId, Long groupId, Long submittedById, ProductType productType) {
        if (groupId != null) {
            return productRepository
                    .findMaxVersionByProjectIdAndGroupIdAndProductType(projectId, groupId, productType)
                    .orElse(0) + 1;
        }
        return productRepository
                .findMaxPersonalVersionByProjectIdAndSubmitterAndProductType(projectId, submittedById, productType)
                .orElse(0) + 1;
    }

    private StoredProductFile storeProductFile(
            MultipartFile file,
            Long projectId,
            Long groupId,
            Integer version,
            ProductType productType
    ) {
        validateProductFile(file, productType);
        String originalFileName = Paths.get(file.getOriginalFilename() == null ? "product" : file.getOriginalFilename())
                .getFileName()
                .toString();
        String extension = getExtension(originalFileName);
        String storedFileName = version + "." + extension;
        Path baseDirectory = getProductStorageDirectory();
        Path productDirectory = groupId == null
                ? baseDirectory.resolve(String.valueOf(projectId)).normalize()
                : baseDirectory.resolve(String.valueOf(projectId)).resolve("groups").resolve(String.valueOf(groupId)).normalize();
        if (!productDirectory.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("Invalid product storage path");
        }
        Path target = productDirectory.resolve(storedFileName).normalize();
        if (!target.startsWith(productDirectory)) {
            throw new IllegalArgumentException("Invalid product storage path");
        }
        try {
            Files.createDirectories(productDirectory);
            Files.copy(file.getInputStream(), target);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot store product file", ex);
        }
        String url = groupId == null
                ? "/storage/products/" + projectId + "/" + storedFileName
                : "/storage/products/" + projectId + "/groups/" + groupId + "/" + storedFileName;
        return new StoredProductFile(url, originalFileName, file.getContentType(), file.getSize());
    }

    private void validateProductFile(MultipartFile file, ProductType productType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Product file is empty");
        }
        if (file.getSize() > MAX_PRODUCT_FILE_SIZE) {
            throw new IllegalArgumentException("Product file must not exceed 50MB");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.getOrDefault(productType, Set.of()).contains(extension)) {
            throw new IllegalArgumentException("File type is not allowed for product type " + productType);
        }
    }

    private String normalizeExternalLink(String externalLink) {
        String value = trimToNull(externalLink);
        if (value == null) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("External link is invalid");
        }
        return value;
    }

    private ProjectEntity findProject(Long projectId) {
        return projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    private Path getProductStorageDirectory() {
        String configuredPath = StringUtils.hasText(productStoragePath) ? productStoragePath : "storage/products";
        return Paths.get(configuredPath).toAbsolutePath().normalize();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }

    private record StoredProductFile(String url, String originalFileName, String contentType, Long size) {
    }
}
