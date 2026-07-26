package com.swipeauctions.storage.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageProvider {

    /** Stores {@code file} under {@code subDir} and returns its public URL path. */
    String store(MultipartFile file, String subDir);

}
