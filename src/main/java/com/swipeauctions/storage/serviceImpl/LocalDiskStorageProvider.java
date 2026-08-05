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
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
        return store(content, file.getOriginalFilename(), file.getContentType(), subDir);
    }

    @Override
    public String store(byte[] content, String originalFilename, String contentType, String subDir) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("No file uploaded");
        }
        boolean isMedia = contentType != null && (contentType.startsWith("image/") || contentType.startsWith("video/"));
        if (!isMedia) {
            throw new BadRequestException("Only image or video uploads are supported");
        }
        // The client-supplied Content-Type header is attacker-controlled — sniff the actual file
        // bytes too, so e.g. an SVG-with-embedded-<script> can't get in by just claiming
        // "image/svg+xml" (SVG is a text/XML format with no binary magic number, so it never
        // matches any signature below and is rejected here regardless of the header it arrives
        // with). See Findings_pendings.md #7.
        if (!hasKnownMediaSignature(content)) {
            throw new BadRequestException("File content doesn't match a supported image or video format");
        }
        String extension = "";
        String original = StringUtils.getFilenameExtension(originalFilename);
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
            Files.write(dir.resolve(filename), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
        return "/uploads/" + subDir + "/" + filename;
    }

    /**
     * Checks the leading bytes of the content against known binary magic numbers for the
     * raster-image and video formats this app actually accepts. Deliberately allowlist-only: an
     * SVG (or any other text/XML-based format) has no binary signature to match, so it's rejected
     * here regardless of what Content-Type it claims — no denylist of "dangerous" formats to keep
     * up to date.
     */
    private boolean hasKnownMediaSignature(byte[] content) {
        int read = Math.min(content.length, 16);
        byte[] header = new byte[16];
        System.arraycopy(content, 0, header, 0, read);
        if (read < 4) {
            return false;
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) return true;                          // JPEG
        if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return true; // PNG
        if (startsWith(header, 0x47, 0x49, 0x46, 0x38)) return true;                    // GIF87a/89a
        if (read >= 12 && startsWith(header, 0x52, 0x49, 0x46, 0x46)                    // RIFF....
                && (matchesAt(header, 8, 0x57, 0x45, 0x42, 0x50)                        // ....WEBP
                || matchesAt(header, 8, 0x41, 0x56, 0x49, 0x20))) return true;          // ....AVI␠
        if (read >= 8 && matchesAt(header, 4, 0x66, 0x74, 0x79, 0x70)) return true;     // ISO base media (mp4/mov/m4v): ....ftyp
        if (startsWith(header, 0x1A, 0x45, 0xDF, 0xA3)) return true;                    // WebM/Matroska (EBML header)
        return false;
    }

    private boolean startsWith(byte[] header, int... expected) {
        return matchesAt(header, 0, expected);
    }

    private boolean matchesAt(byte[] header, int offset, int... expected) {
        if (offset + expected.length > header.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
