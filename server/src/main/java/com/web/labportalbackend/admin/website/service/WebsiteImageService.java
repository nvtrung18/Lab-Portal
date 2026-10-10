package com.web.labportalbackend.admin.website.service;

import com.web.labportalbackend.admin.website.dto.WebsiteImageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class WebsiteImageService {
    private final Path storage;

    public WebsiteImageService(@Value("${app.storage.website-image-path:uploads/website}") String storagePath) {
        storage = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public WebsiteImageResponse upload(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Ảnh phải có dữ liệu và không vượt quá 5 MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            String extension = extension(bytes);
            String name = UUID.randomUUID() + extension;
            Files.createDirectories(storage);
            Files.write(storage.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
            return new WebsiteImageResponse("/api/uploads/website/" + name);
        } catch (IOException ex) {
            throw new UncheckedIOException("Website image cannot be stored.", ex);
        }
    }

    private String extension(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) return ".png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return ".jpg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return ".webp";
        throw new IllegalArgumentException("Chỉ hỗ trợ ảnh PNG, JPEG hoặc WebP.");
    }
}
