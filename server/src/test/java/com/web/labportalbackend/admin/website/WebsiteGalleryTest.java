package com.web.labportalbackend.admin.website;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

class WebsiteGalleryTest {
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
    ObjectNode image(int index) {
        var text = new WebsiteContent.ImageText("Image " + index, "Ảnh " + index);
        return mapper.valueToTree(new WebsiteContent.GalleryImage("photo-" + index, "/api/uploads/website/photo-" + index + ".png", text, text));
    }
    ObjectNode withGallery() throws Exception {
        ObjectNode root = legacy();
        for (String collection : List.of("research", "projects", "news")) {
            for (var record : root.path("data").path(collection)) {
                ObjectNode item = (ObjectNode) record;
                item.putArray("gallery").add(image(1)).add(image(2));
                item.set("imageAlt", image(0).get("alt")); item.set("imageCaption", image(0).get("caption"));
            }
        }
        var text = new WebsiteContent.LocalizedText("Products", "Sản phẩm");
        var section = new WebsiteContent.WebsiteSection("gallery-product", text, text, text, List.of(), "/api/uploads/website/cover.png", "split", text, "", true,
                new WebsiteContent.RichTextContent("<p><strong>Original</strong></p>", "<p><a href=\"https://example.org\">Nội dung</a></p>"),
                List.of(mapper.treeToValue(image(1), WebsiteContent.GalleryImage.class)), new WebsiteContent.ImageText("Cover", "Ảnh đại diện"), new WebsiteContent.ImageText("Caption", "Chú thích"));
        root.putArray("contentLibrary").add(mapper.valueToTree(section));
        root.putArray("pages").add(mapper.valueToTree(new WebsiteContent.WebsitePage("products", "products", text, text, text, "products", true, true, List.of(), List.of(section.id()), null, null)));
        return root;
    }
    WebsiteContent content(ObjectNode node) throws Exception { return mapper.treeToValue(node, WebsiteContent.class); }
    SystemConfigEntity row(WebsiteState state) throws Exception {
        var row = new SystemConfigEntity(); row.setConfigKey("PUBLIC_WEBSITE_CONTENT"); row.setConfigValueJson(mapper.writeValueAsString(state)); return row;
    }
    void admin() {
        var actor = mock(User.class); when(actor.hasRole("ADMIN")).thenReturn(true); when(users.findByUsername("admin")).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "unused", List.of()));
    }
    boolean valid(ObjectNode node) throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) { return factory.getValidator().validate(content(node)).isEmpty(); }
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void legacyMissingAndNullGalleryRoundTripPreservesCoverAndBody() throws Exception {
        var original = content(legacy());
        assertNull(original.data().research().getFirst().gallery());
        var restored = mapper.readValue(mapper.writeValueAsString(original), WebsiteContent.class);
        assertEquals(original, restored);
        assertEquals(original.data().news().getFirst().image(), restored.data().news().getFirst().image());
        assertTrue(valid(legacy()));
    }
    @Test void bilingualGalleriesAndCoverMetadataRoundTripForEveryImageBearingType() throws Exception {
        var content = content(withGallery());
        assertTrue(valid(withGallery()));
        assertEquals(content, mapper.readValue(mapper.writeValueAsString(content), WebsiteContent.class));
        assertEquals("Ảnh 1", content.data().research().getFirst().gallery().getFirst().alt().vi());
        assertEquals(2, content.data().projects().getFirst().gallery().size());
        assertEquals(2, content.data().news().getFirst().gallery().size());
        assertEquals("Chú thích", content.contentLibrary().getFirst().imageCaption().vi());
    }
    @Test void maximumFifteenAdditionalImagesIsEnforcedForAllFourTypes() throws Exception {
        for (String target : List.of("research", "projects", "news", "contentLibrary")) {
            ObjectNode node = withGallery();
            ObjectNode owner = (ObjectNode) (target.equals("contentLibrary") ? node.path(target).get(0) : node.path("data").path(target).get(0));
            ArrayNode gallery = owner.putArray("gallery");
            for (int i = 0; i < 15; i++) gallery.add(image(i));
            assertTrue(valid(node), target);
            gallery.add(image(15)); assertFalse(valid(node), target);
        }
    }
    @Test void invalidSourcesNullEntriesMissingMetadataAndOversizedTextAreRejected() throws Exception {
        for (String src : List.of("javascript:alert(1)", "data:image/png;base64,AAAA", "//example.org/x.png", "")) {
            var node = withGallery();
            ((ObjectNode) node.path("contentLibrary").get(0).path("gallery").get(0)).put("src", src);
            assertFalse(valid(node), src);
        }
        var nullItem = withGallery(); ((ArrayNode) nullItem.path("contentLibrary").get(0).path("gallery")).addNull(); assertFalse(valid(nullItem));
        var missingAlt = withGallery(); ((ObjectNode) missingAlt.path("contentLibrary").get(0).path("gallery").get(0)).remove("alt"); assertFalse(valid(missingAlt));
        var longCaption = withGallery(); ((ObjectNode) longCaption.path("contentLibrary").get(0).path("imageCaption")).put("vi", "x".repeat(2001)); assertFalse(valid(longCaption));
    }
    @Test void savingDraftDoesNotChangePublishedGalleryAndPublishingEnforcesRevision() throws Exception {
        admin(); var old = content(legacy()); var updated = content(withGallery());
        var row = row(new WebsiteState(10, old, old, Instant.parse("2026-10-01T00:00:00Z")));
        when(repository.findByConfigKeyForUpdate("PUBLIC_WEBSITE_CONTENT")).thenReturn(Optional.of(row));
        var draft = service.saveDraft(new WebsiteSaveRequest(10L, updated));
        assertEquals(old, draft.published()); assertEquals(updated, draft.draft());
        assertEquals(draft, mapper.readValue(row.getConfigValueJson(), WebsiteState.class));
        assertThrows(WebsiteRevisionConflictException.class, () -> service.publish(new WebsiteSaveRequest(10L, updated)));
        var published = service.publish(new WebsiteSaveRequest(11L, updated));
        assertEquals(updated, published.published()); assertEquals(12, published.revision());
    }
    @Test void publicProjectionKeepsGalleriesWhileStrippingPrivateIdsAndUnselectedLibrary() throws Exception {
        var original = content(withGallery()); var row = row(new WebsiteState(1, original, original, Instant.now()));
        String before = row.getConfigValueJson();
        when(repository.findByConfigKeyAndDeletedFalse("PUBLIC_WEBSITE_CONTENT")).thenReturn(Optional.of(row));
        when(scholar.overlay(anyList())).thenAnswer(call -> call.getArgument(0));
        var visible = service.getPublished().content();
        assertEquals(original.data().research().getFirst().gallery(), visible.data().research().getFirst().gallery());
        assertEquals(original.data().research().getFirst().imageAlt(), visible.data().research().getFirst().imageAlt());
        assertEquals(original.data().projects(), visible.data().projects()); assertEquals(original.data().news(), visible.data().news());
        assertEquals(original.contentLibrary(), visible.pages().getFirst().sections());
        assertNull(visible.contentLibrary()); assertNull(visible.data().research().getFirst().labId());
        assertTrue(visible.data().members().stream().allMatch(member -> member.userId() == null));
        assertEquals(before, row.getConfigValueJson()); verify(repository, never()).save(any());
    }
    @Test void duplicateGalleryIdsAreRejectedBeforeWritingAndReuseAcrossArticlesIsAllowed() throws Exception {
        admin(); var node = withGallery(); var gallery = (ArrayNode) node.path("contentLibrary").get(0).path("gallery"); gallery.add(image(1));
        assertThrows(IllegalArgumentException.class, () -> service.saveDraft(new WebsiteSaveRequest(0L, content(node))));
        verify(repository, never()).save(any());
        var good = content(withGallery()); var row = row(new WebsiteState(0, good, good, Instant.now()));
        when(repository.findByConfigKeyForUpdate("PUBLIC_WEBSITE_CONTENT")).thenReturn(Optional.of(row));
        assertDoesNotThrow(() -> service.saveDraft(new WebsiteSaveRequest(0L, good)));
    }
    @Test void nonAdminCannotSaveOrPublishGalleryContent() throws Exception {
        var data = content(withGallery());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.saveDraft(new WebsiteSaveRequest(0L, data)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.publish(new WebsiteSaveRequest(0L, data)));
        verify(repository, never()).save(any());
    }
}
