package com.web.labportalbackend.admin.website;

import com.web.labportalbackend.admin.website.service.WebsiteImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class WebsiteGalleryUploadTest {
    @TempDir Path storage;
    final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @Test void repeatedUploadsHaveIndependentNamesAndRetainAllStoredFiles() throws Exception {
        var service = new WebsiteImageService(storage.toString());
        var first = service.upload(new MockMultipartFile("file", "same.png", "image/png", png));
        var second = service.upload(new MockMultipartFile("file", "same.png", "image/png", png));
        assertNotEquals(first.url(), second.url());
        try (var files = Files.list(storage)) { assertEquals(2, files.count()); }
        assertArrayEquals(png, Files.readAllBytes(storage.resolve(Path.of(first.url()).getFileName())));
    }
    @Test void magicBytesRemainAuthoritativeAndUnsupportedContentIsRejected() {
        var service = new WebsiteImageService(storage.toString());
        assertThrows(IllegalArgumentException.class, () -> service.upload(new MockMultipartFile("file", "fake.png", "image/png", "<svg/>".getBytes())));
        assertTrue(service.upload(new MockMultipartFile("file", "image.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})).url().endsWith(".jpg"));
        assertTrue(service.upload(new MockMultipartFile("file", "image.webp", "image/webp", "RIFF0000WEBP".getBytes())).url().endsWith(".webp"));
    }
    @Test void existingFiveMegabyteLimitAllowsBoundaryAndRejectsEmptyOrLargerFiles() {
        var service = new WebsiteImageService(storage.toString());
        assertThrows(IllegalArgumentException.class, () -> service.upload(new MockMultipartFile("file", new byte[0])));
        byte[] exact = Arrays.copyOf(png, 5 * 1024 * 1024);
        assertDoesNotThrow(() -> service.upload(new MockMultipartFile("file", "image.png", "image/png", exact)));
        byte[] larger = Arrays.copyOf(png, exact.length + 1);
        assertThrows(IllegalArgumentException.class, () -> service.upload(new MockMultipartFile("file", "large.png", "image/png", larger)));
    }
}
