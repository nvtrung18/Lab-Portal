package com.web.labportalbackend.admin.website;

import com.web.labportalbackend.admin.website.controller.ScholarSyncController;
import com.web.labportalbackend.admin.website.scholar.ScholarSyncService;
import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=ScholarSyncController.class,excludeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=JwtAuthenticationFilter.class))
@Import(ScholarSyncControllerTest.Security.class)
class ScholarSyncControllerTest {
    @TestConfiguration @EnableMethodSecurity static class Security {}
    @Autowired MockMvc mvc;
    @MockitoBean ScholarSyncService service;
    @Test void adminCanReadStatusAndRequestSyncWithoutReceivingKeys() throws Exception {
        when(service.status()).thenReturn(new ScholarSyncService.Status(false,false,List.of()));
        mvc.perform(get("/admin/website/scholar-sync").with(user("a").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.configured").value(false)).andExpect(jsonPath("$.data.apiKey").doesNotExist());
        mvc.perform(post("/admin/website/scholar-sync/author").with(csrf()).with(user("a").roles("ADMIN"))).andExpect(status().isAccepted());verify(service).syncNow("author");
    }
    @Test void otherRolesCannotReadOrTriggerSync() throws Exception {
        mvc.perform(get("/admin/website/scholar-sync").with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/website/scholar-sync/author").with(csrf()).with(user("m").roles("LAB_MANAGER"))).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
}
