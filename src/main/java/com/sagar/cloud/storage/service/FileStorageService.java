package com.sagar.cloud.storage.service;

import com.sagar.cloud.storage.dto.FileResponse;
import com.sagar.cloud.storage.entity.FileMetadata;
import com.sagar.cloud.storage.entity.User;
import com.sagar.cloud.storage.repository.FileMetadataRepository;
import com.sagar.cloud.storage.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileMetadataRepository fileMetadataRepository;
    private final UserRepository userRepository;

    private static final long MAX_STORAGE_PER_USER = 20 * 1024 * 1024L;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        try {
            this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    @Transactional
    public FileResponse uploadFile(MultipartFile file, String userEmail) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        Long currentStorageUsed = fileMetadataRepository.getTotalStorageUsedByUser(user);
        long usedBytes = (currentStorageUsed != null) ? currentStorageUsed : 0L;

        if (usedBytes + file.getSize() > MAX_STORAGE_PER_USER) {
            long remainingBytes = Math.max(0, MAX_STORAGE_PER_USER - usedBytes);
            throw new IllegalArgumentException(String.format(
                    "Storage quota exceeded! Used: %.2f MB, Allowed: %.2f MB, Remaining: %.2f MB",
                    usedBytes / (1024.0 * 1024.0),
                    MAX_STORAGE_PER_USER / (1024.0 * 1024.0),
                    remainingBytes / (1024.0 * 1024.0)
            ));
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.contains("..")) {
            throw new IllegalArgumentException("Invalid file path or name");
        }

        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
        Path targetLocation = this.rootLocation.resolve(uniqueFileName);

        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        FileMetadata metadata = FileMetadata.builder()
                .fileName(originalFileName)
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .storagePath(targetLocation.toString())
                .user(user)
                .isShared(false)
                .build();

        FileMetadata savedMetadata = fileMetadataRepository.save(metadata);

        return toFileResponse(savedMetadata);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> getUserFiles(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return fileMetadataRepository.findByUser(user)
                .stream()
                .map(this::toFileResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Resource downloadFile(Long fileId, String userEmail) throws MalformedURLException {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        FileMetadata metadata = fileMetadataRepository.findByIdAndUser(fileId, user)
                .orElseThrow(() -> new IllegalArgumentException("File not found or unauthorized"));

        Path filePath = Paths.get(metadata.getStoragePath()).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("Could not read the file: " + metadata.getFileName());
        }

        return resource;
    }

    @Transactional(readOnly = true)
    public FileMetadata getFileMetadata(Long fileId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return fileMetadataRepository.findByIdAndUser(fileId, user)
                .orElseThrow(() -> new IllegalArgumentException("File not found or unauthorized"));
    }

    @Transactional
    public void deleteFile(Long fileId, String userEmail) throws IOException {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        FileMetadata metadata = fileMetadataRepository.findByIdAndUser(fileId, user)
                .orElseThrow(() -> new IllegalArgumentException("File not found or unauthorized"));

        Path filePath = Paths.get(metadata.getStoragePath()).normalize();
        Files.deleteIfExists(filePath);

        fileMetadataRepository.delete(metadata);
    }

    @Transactional
    public String generateShareLink(Long fileId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        FileMetadata metadata = fileMetadataRepository.findByIdAndUser(fileId, user)
                .orElseThrow(() -> new IllegalArgumentException("File not found or unauthorized"));

        if (metadata.getShareToken() == null) {
            metadata.setShareToken(UUID.randomUUID().toString());
            metadata.setShared(true);
            fileMetadataRepository.save(metadata);
        }

        return metadata.getShareToken();
    }

    @Transactional
    public void revokeShareLink(Long fileId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        FileMetadata metadata = fileMetadataRepository.findByIdAndUser(fileId, user)
                .orElseThrow(() -> new IllegalArgumentException("File not found or unauthorized"));

        metadata.setShareToken(null);
        metadata.setShared(false);
        fileMetadataRepository.save(metadata);
    }

    @Transactional(readOnly = true)
    public Resource downloadPublicFile(String shareToken) throws MalformedURLException {
        FileMetadata metadata = fileMetadataRepository.findByShareToken(shareToken)
                .filter(FileMetadata::isShared)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or disabled share link"));

        Path filePath = Paths.get(metadata.getStoragePath()).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("Could not read the file: " + metadata.getFileName());
        }

        return resource;
    }

    @Transactional(readOnly = true)
    public FileMetadata getFileMetadataByShareToken(String shareToken) {
        return fileMetadataRepository.findByShareToken(shareToken)
                .filter(FileMetadata::isShared)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or disabled share link"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserStorageUsage(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        Long currentStorageUsed = fileMetadataRepository.getTotalStorageUsedByUser(user);
        long usedBytes = (currentStorageUsed != null) ? currentStorageUsed : 0L;
        long totalBytes = MAX_STORAGE_PER_USER;

        Map<String, Object> usage = new HashMap<>();
        usage.put("usedBytes", usedBytes);
        usage.put("totalBytes", totalBytes);
        usage.put("usedMB", String.format("%.2f MB", usedBytes / (1024.0 * 1024.0)));
        usage.put("totalMB", String.format("%.2f MB", totalBytes / (1024.0 * 1024.0)));
        usage.put("usagePercentage", String.format("%.1f%%", (usedBytes * 100.0) / totalBytes));

        return usage;
    }

    private FileResponse toFileResponse(FileMetadata metadata) {
        return FileResponse.builder()
                .id(metadata.getId())
                .fileName(metadata.getFileName())
                .fileType(metadata.getFileType())
                .fileSize(metadata.getFileSize())
                .uploadedAt(metadata.getUploadedAt())
                .build();
    }
}