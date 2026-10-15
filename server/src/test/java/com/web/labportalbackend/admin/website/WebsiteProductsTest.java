package com.web.labportalbackend.admin.website;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.admin.website.dto.*;
import com.web.labportalbackend.admin.website.scholar.ScholarSnapshotStore;
import com.web.labportalbackend.admin.website.service.impl.WebsiteContentServiceImpl;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.common.exception.WebsiteRevisionConflictException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebsiteProductsTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final SystemConfigRepository repository = mock(SystemConfigRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final LaboratoryRepository labs = mock(LaboratoryRepository.class);
    final AuditLogService audit = mock(AuditLogService.class);
    final ScholarSnapshotStore scholar = mock(ScholarSnapshotStore.class);
    final WebsiteContentServiceImpl service = new WebsiteContentServiceImpl(repository,mapper,users,labs,audit,scholar);
    WebsiteContent content() throws Exception {
        Path fixture = fixturePath();
        var original = mapper.treeToValue(mapper.readTree(Files.readString(fixture)).get("content"),WebsiteContent.class);
        var t = new WebsiteContent.LocalizedText("Products","Sản phẩm");
        var html = new WebsiteContent.RichTextContent("<p><strong>Product</strong></p>","<p><a href=\"https://example.org\">Sản phẩm</a><img src=\"/api/uploads/website/a.png\" alt=\"Ảnh\"></p>");
        var section = new WebsiteContent.WebsiteSection("product",t,t,t,List.of(),"/api/uploads/website/a.png","split",t,"https://example.org",true,html,null,null,null);
        var product = new WebsiteContent.WebsitePage("products","products",t,t,t,"products",true,true,List.of(section),List.of("product"),null);
        var news = new WebsiteContent.WebsitePage("news","news",t,t,t,"news",true,true,List.of(),List.of(),null);
        return new WebsiteContent(original.profile(),original.data(),original.copy(),original.media(),List.of(product,news),List.of(section));
    }
    Path fixturePath() { return java.util.stream.Stream.of(Path.of("../contracts/website-demo-content.json"),Path.of("contracts/website-demo-content.json")).filter(Files::exists).findFirst().orElseThrow(); }
    SystemConfigEntity storage(WebsiteState state) throws Exception { var e=new SystemConfigEntity();e.setConfigKey("PUBLIC_WEBSITE_CONTENT");e.setConfigValueJson(mapper.writeValueAsString(state));return e; }
    void admin() {
        var actor=mock(User.class);when(actor.hasRole("ADMIN")).thenReturn(true);when(users.findByUsername("admin")).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","unused",List.of()));
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    @Test void productsTemplateValidatesAndSurvivesJsonRoundTripWithBothLanguagesAndImages() throws Exception {
        var content=content();
        try(var factory=Validation.buildDefaultValidatorFactory()) { assertTrue(factory.getValidator().validate(content).isEmpty()); }
        var restored=mapper.readValue(mapper.writeValueAsString(content),WebsiteContent.class);
        assertEquals(content,restored);assertEquals("products",restored.pages().getFirst().template());
    }
    @Test void savingDraftPreservesPublishedUntilExplicitPublishAndEnforcesRevision() throws Exception {
        admin(); var content=content(); var old=new WebsiteState(4,content,content,Instant.parse("2026-10-01T00:00:00Z")); var row=storage(old);
        when(repository.findByConfigKeyForUpdate("PUBLIC_WEBSITE_CONTENT")).thenReturn(Optional.of(row));
        var saved=service.saveDraft(new WebsiteSaveRequest(4L,content));
        assertEquals(5,saved.revision());assertEquals(old.published(),saved.published());assertEquals(old.publishedAt(),saved.publishedAt());
        assertThrows(WebsiteRevisionConflictException.class,()->service.publish(new WebsiteSaveRequest(4L,content)));
        var published=service.publish(new WebsiteSaveRequest(5L,content));assertEquals(6,published.revision());assertEquals(content,published.published());
    }
    @Test void publicProjectionResolvesProductsAndNeverWritesDraftOrLeaksLibrary() throws Exception {
        var content=content();var row=storage(new WebsiteState(8,content,content,Instant.now()));String original=row.getConfigValueJson();
        when(repository.findByConfigKeyAndDeletedFalse("PUBLIC_WEBSITE_CONTENT")).thenReturn(Optional.of(row));
        when(scholar.overlay(anyList())).thenAnswer(call->call.getArgument(0));
        var published=service.getPublished().content();
        assertNull(published.contentLibrary());assertNull(published.pages().getFirst().sectionIds());
        assertEquals(content.contentLibrary(),published.pages().getFirst().sections());
        assertTrue(published.pages().get(1).sections().isEmpty());assertEquals(original,row.getConfigValueJson());verify(repository,never()).save(any());
        assertTrue(published.data().members().stream().allMatch(member->member.userId()==null));
    }
}
