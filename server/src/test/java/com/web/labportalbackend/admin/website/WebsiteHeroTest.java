package com.web.labportalbackend.admin.website;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.admin.website.dto.*;
import com.web.labportalbackend.admin.website.scholar.ScholarSnapshotStore;
import com.web.labportalbackend.admin.website.service.impl.WebsiteContentServiceImpl;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.WebsiteRevisionConflictException;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebsiteHeroTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final SystemConfigRepository repository = mock(SystemConfigRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final LaboratoryRepository labs = mock(LaboratoryRepository.class);
    final AuditLogService audit = mock(AuditLogService.class);
    final ScholarSnapshotStore scholar = mock(ScholarSnapshotStore.class);
    final WebsiteContentServiceImpl service = new WebsiteContentServiceImpl(repository, mapper, users, labs, audit, scholar);

    ObjectNode legacy() throws Exception {
        Path fixture = java.util.stream.Stream.of(Path.of("../contracts/website-demo-content.json"), Path.of("contracts/website-demo-content.json"))
                .filter(Files::exists).findFirst().orElseThrow();
        return (ObjectNode) mapper.readTree(Files.readString(fixture)).get("content");
    }
    ObjectNode homepage(ObjectNode node) {
        for (var page : node.path("pages")) if (page.path("template").asText().equals("home")) return (ObjectNode) page;
        throw new IllegalStateException("Missing homepage fixture");
    }
    WebsiteContent.WebsiteHero hero() {
        var title = new WebsiteContent.HeroText("English Hero title", "Tiêu đề Hero tiếng Việt");
        var primary = new WebsiteContent.HeroCta(new WebsiteContent.HeroText("Products", "Nghiên cứu"), new WebsiteContent.HeroUrl("/en/products", "/vi/research?from=hero#research"));
        var secondary = new WebsiteContent.HeroCta(new WebsiteContent.HeroText("Collaborate", "Liên hệ"), new WebsiteContent.HeroUrl("https://example.org/contact", "#contact"));
        return new WebsiteContent.WebsiteHero(title, new WebsiteContent.HeroText("Research group", "Nhóm nghiên cứu"),
                new WebsiteContent.LocalizedText("English description\nSecond line", "Mô tả tiếng Việt\nDòng thứ hai"), primary, secondary,
                "/api/uploads/website/new-background.png", new WebsiteContent.HeroText("Background", "Ảnh nền"), new WebsiteContent.HeroText("Discover together", "Cùng khám phá"));
    }
    ObjectNode configured() throws Exception {
        var node = legacy(); homepage(node).set("hero", mapper.valueToTree(hero())); return node;
    }
    WebsiteContent content(ObjectNode node) throws Exception { return mapper.treeToValue(node, WebsiteContent.class); }
    boolean valid(ObjectNode node) throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) { return factory.getValidator().validate(content(node)).isEmpty(); }
    }
    SystemConfigEntity row(WebsiteState state) throws Exception {
        var row = new SystemConfigEntity(); row.setConfigKey("PUBLIC_WEBSITE_CONTENT"); row.setConfigValueJson(mapper.writeValueAsString(state)); return row;
    }
    void admin() {
        var actor = mock(User.class); when(actor.hasRole("ADMIN")).thenReturn(true); when(users.findByUsername("admin")).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "unused", List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void legacyMissingOrNullHeroStaysOptionalAndRetainsEntireSavedContent() throws Exception {
        var original = content(legacy());
        assertNull(original.pages().getFirst().hero()); assertTrue(valid(legacy()));
        var node = legacy(); homepage(node).putNull("hero");
        assertEquals(original, content(node));
        assertEquals(original, mapper.readValue(mapper.writeValueAsString(original), WebsiteContent.class));
    }
    @Test void bilingualHeroUrlsAndUploadedImageSurviveFullJsonRoundTrip() throws Exception {
        var node = configured(); var configured = content(node);
        assertTrue(valid(node));
        assertEquals(configured, mapper.readValue(mapper.writeValueAsString(configured), WebsiteContent.class));
        assertEquals(hero(), configured.pages().getFirst().hero());
        assertEquals(content(legacy()).data(), configured.data());
        assertEquals(content(legacy()).pages().subList(1, configured.pages().size()), configured.pages().subList(1, configured.pages().size()));
    }
    @Test void executableProtocolRelativeAndWhitespaceCtaUrlsAreRejectedInEitherLanguage() throws Exception {
        for (String button : List.of("primaryCta", "secondaryCta")) for (String language : List.of("vi", "en")) {
            for (String bad : List.of("javascript:alert(1)", "//example.org", "data:text/html,x", "file:///tmp/x", "products", "https://example.org/a b")) {
                var node = configured(); ((ObjectNode) homepage(node).path("hero").path(button).path("url")).put(language, bad);
                assertFalse(valid(node), button + ":" + language + ":" + bad);
            }
        }
    }
    @Test void missingRequiredHeroFieldsInvalidBackgroundAndOversizedContentAreRejected() throws Exception {
        var missing = configured(); ((ObjectNode) homepage(missing).path("hero")).remove("primaryCta"); assertFalse(valid(missing));
        var missingLanguage = configured(); ((ObjectNode) homepage(missingLanguage).path("hero").path("title")).remove("en"); assertFalse(valid(missingLanguage));
        var longTitle = configured(); ((ObjectNode) homepage(longTitle).path("hero").path("title")).put("vi", "x".repeat(2001)); assertFalse(valid(longTitle));
        var longDescription = configured(); ((ObjectNode) homepage(longDescription).path("hero").path("description")).put("en", "x".repeat(20001)); assertFalse(valid(longDescription));
        for (String url : List.of("javascript:x", "//example.org/a.png", "data:image/png;base64,AAAA")) {
            var image = configured(); ((ObjectNode) homepage(image).path("hero")).put("backgroundImage", url); assertFalse(valid(image));
        }
    }
    @Test void explicitEmptyStringsRemainValidAndDoNotRestoreLegacyText() throws Exception {
        var node = configured(); var hero = (ObjectNode) homepage(node).path("hero");
        ((ObjectNode) hero.path("primaryCta").path("label")).put("vi", "");
        ((ObjectNode) hero.path("primaryCta").path("url")).put("vi", "");
        hero.put("backgroundImage", ""); assertTrue(valid(node));
        var saved = content(node).pages().getFirst().hero();
        assertEquals("", saved.primaryCta().url().vi()); assertEquals("", saved.backgroundImage());
    }
    @Test void saveDraftPreservesPublishedHeroUntilPublishAndEnforcesRevisionConflict() throws Exception {
        admin(); var old = content(legacy()); var edited = content(configured());
        var row = row(new WebsiteState(4, old, old, Instant.parse("2026-10-01T00:00:00Z")));
        when(repository.findByConfigKeyForUpdate("PUBLIC_WEBSITE_CONTENT")).thenReturn(Optional.of(row));
        var draft = service.saveDraft(new WebsiteSaveRequest(4L, edited));
        assertEquals(old, draft.published()); assertEquals(edited, draft.draft()); assertEquals(5, draft.revision());
        assertThrows(WebsiteRevisionConflictException.class, () -> service.publish(new WebsiteSaveRequest(4L, edited)));
        var published = service.publish(new WebsiteSaveRequest(5L, edited));
        assertEquals(hero(), published.published().pages().getFirst().hero()); assertEquals(6, published.revision());
        assertEquals(published, mapper.readValue(row.getConfigValueJson(), WebsiteState.class));
    }
    @Test void publicProjectionRetainsHeroWithoutWritingDraftOrLeakingPrivateMappings() throws Exception {
        var original = content(configured()); var row = row(new WebsiteState(1, original, original, Instant.now())); var before = row.getConfigValueJson();
        when(repository.findByConfigKeyAndDeletedFalse("PUBLIC_WEBSITE_CONTENT")).thenReturn(Optional.of(row));
        when(scholar.overlay(anyList())).thenAnswer(call -> call.getArgument(0));
        var published = service.getPublished().content();
        assertEquals(hero(), published.pages().getFirst().hero());
        assertNull(published.contentLibrary()); assertNull(published.pages().getFirst().sectionIds());
        assertTrue(published.data().members().stream().allMatch(member -> member.userId() == null));
        assertEquals(before, row.getConfigValueJson()); verify(repository, never()).save(any());
    }
    @Test void heroOnNonHomepageIsRejectedWithoutWritingAnything() throws Exception {
        admin(); var node = legacy(); ((ObjectNode) node.path("pages").get(1)).set("hero", mapper.valueToTree(hero()));
        assertThrows(IllegalArgumentException.class, () -> service.saveDraft(new WebsiteSaveRequest(0L, content(node))));
        verify(repository, never()).save(any());
    }
    @Test void nonAdminCannotSaveOrPublishHero() throws Exception {
        var edited = content(configured());
        assertThrows(AccessDeniedException.class, () -> service.saveDraft(new WebsiteSaveRequest(0L, edited)));
        assertThrows(AccessDeniedException.class, () -> service.publish(new WebsiteSaveRequest(0L, edited)));
        verify(repository, never()).save(any());
    }
}
