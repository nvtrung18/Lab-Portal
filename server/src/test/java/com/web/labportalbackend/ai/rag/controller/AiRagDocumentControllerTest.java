package com.web.labportalbackend.ai.rag.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.rag.dto.response.AiRagDocumentResponse;
import com.web.labportalbackend.ai.rag.enums.AiRagVisibility;
import com.web.labportalbackend.ai.rag.service.AiRagIngestionService;
import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AiRagDocumentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(AiRagDocumentControllerTest.MethodSecurityTestConfig.class)
class AiRagDocumentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AiRagIngestionService ingestionService;

    @Test
    void laboratoryManagerCanSubmitAValidScopedDocument() throws Exception {
        when(ingestionService.ingest(any())).thenReturn(new AiRagDocumentResponse(
                100L, "lab-knowledge", AiAssistantDomain.LAB, "policy-1", 1,
                "LAB_POLICY", AiRagVisibility.LAB_MEMBERS, 1));

        mockMvc.perform(post("/api/ai/rag/documents")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("manager").roles("LAB_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.namespace").value("lab-knowledge"))
                .andExpect(jsonPath("$.data.chunkCount").value(1));
    }

    @Test
    void studentCannotReachIngestionService() throws Exception {
        mockMvc.perform(post("/api/ai/rag/documents")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ingestionService);
    }

    @Test
    void authorityInjectingUnknownFieldIsRejected() throws Exception {
        mockMvc.perform(post("/api/ai/rag/documents")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("manager").roles("LAB_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("}", ",\"ownerId\":999}")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ingestionService);
    }

    @Test
    void managerCanReindexAndRevokeManagedDocument() throws Exception {
        when(ingestionService.reindex(any(), any())).thenReturn(new AiRagDocumentResponse(
                101L, "lab-knowledge", AiAssistantDomain.LAB, "policy-1", 2,
                "LAB_POLICY", AiRagVisibility.LAB_MEMBERS, 1));

        mockMvc.perform(put("/api/ai/rag/documents/100")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("manager").roles("LAB_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"version\":1", "\"version\":2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(101L))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(delete("/api/ai/rag/documents/101")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("manager").roles("LAB_MANAGER")))
                .andExpect(status().isOk());

        verify(ingestionService).reindex(org.mockito.ArgumentMatchers.eq(100L), any());
        verify(ingestionService).revoke(101L);
    }

    @Test
    void studentCannotReindexOrRevokeDocuments() throws Exception {
        mockMvc.perform(put("/api/ai/rag/documents/100")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"version\":1", "\"version\":2")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/ai/rag/documents/100")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ingestionService);
    }

    private static String validBody() {
        return """
                {"domain":"LAB","resourceId":"policy-1","version":1,"sourceType":"LAB_POLICY",
                 "title":"Safety policy","content":"Authorized policy text.",
                 "visibility":"LAB_MEMBERS","labId":10}
                """;
    }

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
