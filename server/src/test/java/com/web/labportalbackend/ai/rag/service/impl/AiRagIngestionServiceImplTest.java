package com.web.labportalbackend.ai.rag.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.rag.dto.request.AiRagDocumentIngestRequest;
import com.web.labportalbackend.ai.rag.entity.AiRagChunkEntity;
import com.web.labportalbackend.ai.rag.entity.AiRagDocumentEntity;
import com.web.labportalbackend.ai.rag.enums.AiRagVisibility;
import com.web.labportalbackend.ai.rag.repository.AiRagChunkRepository;
import com.web.labportalbackend.ai.rag.repository.AiRagDocumentRepository;
import com.web.labportalbackend.ai.rag.service.AiRagTextChunker;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AiRagIngestionServiceImplTest {

    @Mock private AiRagDocumentRepository documentRepository;
    @Mock private AiRagChunkRepository chunkRepository;
    @Mock private UserRepository userRepository;
    @Mock private LaboratoryRepository laboratoryRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private GroupRepository groupRepository;

    private AiRagIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiRagIngestionServiceImpl(documentRepository, chunkRepository, new AiRagTextChunker(),
                userRepository, laboratoryRepository, projectRepository, groupRepository);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("manager", "n/a", List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistsVisibilityAndScopeMetadataOnEveryChunk() {
        User manager = actor("LAB_MANAGER");
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(10L, 7L)).thenReturn(true);
        when(documentRepository.existsByNamespaceAndResourceIdAndActiveTrueAndDeletedFalse(
                "lab-knowledge", "policy-1")).thenReturn(false);
        when(documentRepository.save(any())).thenAnswer(invocation -> {
            AiRagDocumentEntity document = invocation.getArgument(0);
            document.setId(100L);
            return document;
        });

        var response = service.ingest(labRequest());

        assertEquals(100L, response.documentId());
        assertEquals("lab-knowledge", response.namespace());
        assertEquals(2, response.chunkCount());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiRagChunkEntity>> chunks = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(chunks.capture());
        assertTrue(chunks.getValue().stream().allMatch(chunk ->
                chunk.getDocumentId().equals(100L)
                        && chunk.getNamespace().equals("lab-knowledge")
                        && chunk.getDomain() == AiAssistantDomain.LAB
                        && chunk.getVisibility() == AiRagVisibility.LAB_MEMBERS
                        && chunk.getOwnerId().equals(7L)
                        && chunk.getLabId().equals(10L)
                        && chunk.getProjectId() == null
                        && chunk.getGroupId() == null
                        && chunk.getContentHash().length() == 64));
    }

    @Test
    void nonOwnerCannotIngestIntoLaboratoryNamespace() {
        User student = actor("STUDENT");
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(student));

        assertThrows(AccessDeniedException.class, () -> service.ingest(labRequest()));

        verify(documentRepository, never()).save(any());
        verify(chunkRepository, never()).saveAll(any());
    }

    private static User actor(String roleName) {
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("manager");
        actor.setStatus(UserStatus.ACTIVE);
        actor.setActive(true);
        actor.setDeleted(false);
        actor.setRoles(Set.of(new Role(roleName, roleName)));
        return actor;
    }

    private static AiRagDocumentIngestRequest labRequest() {
        AiRagDocumentIngestRequest request = new AiRagDocumentIngestRequest();
        request.setDomain(AiAssistantDomain.LAB);
        request.setResourceId("policy-1");
        request.setVersion(1);
        request.setSourceType("LAB_POLICY");
        request.setTitle("Safety policy");
        request.setContent("First page.\fSecond page.");
        request.setVisibility(AiRagVisibility.LAB_MEMBERS);
        request.setLabId(10L);
        return request;
    }
}
