package com.swipeauctions.storage.serviceImpl;

import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.storage.service.StorageProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * No cloud storage vendor is wired up yet — stores uploads on local disk under {@code app.upload.dir},
 * served back at {@code /uploads/**}. Swap in a real provider (S3/Supabase Storage/etc.) by replacing
 * this bean with one implementing {@link StorageProvider}; every caller already goes through that
 * interface so no other code changes.
 */
@Service
public class LocalDiskStorageProvider implements StorageProvider {

    private final Path root;

    public LocalDiskStorageProvider(@Value("${app.upload.dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file uploaded");
        }
        String contentType = file.getContentType();
        boolean isMedia = contentType != null && (contentType.startsWith("image/") || contentType.startsWith("video/"));
        if (!isMedia) {
            throw new BadRequestException("Only image or video uploads are supported");
        }
        String extension = "";
        String original = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (original != null && !original.isBlank()) {
            extension = "." + original.toLowerCase().replaceAll("[^a-z0-9]", "");
        }
        String filename = UUID.randomUUID() + extension;
        try {
            Path dir = root.resolve(subDir).normalize();
            if (!dir.startsWith(root)) {
                throw new BadRequestException("Invalid upload path");
            }
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
        return "/uploads/" + subDir + "/" + filename;
    }
}
