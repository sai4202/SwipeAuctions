package com.swipeauctions.storage.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageProvider {

    /** Stores {@code file} under {@code subDir} and returns its public URL path. */
    String store(MultipartFile file, String subDir);

    /** Same as {@link #store(MultipartFile, String)}, for content that didn't arrive as a
     *  {@code MultipartFile} — e.g. one image extracted from a bulk-upload ZIP. */
    String store(byte[] content, String originalFilename, String contentType, String subDir);

}
