package com.sagar.cloud.storage.controller;

import com.sagar.cloud.storage.dto.FileResponse;
import com.sagar.cloud.storage.entity.FileMetadata;
import com.sagar.cloud.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    // 1. Upload a file (Protected - checks 20MB quota)
    @PostMapping("/upload")
    public ResponseEntity<FileResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        String userEmail = authentication.getName();
        FileResponse response = fileStorageService.uploadFile(file, userEmail);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. List all files belonging to the logged-in user (Protected)
    @GetMapping
    public ResponseEntity<List<FileResponse>> listUserFiles(Authentication authentication) {
        String userEmail = authentication.getName();
        List<FileResponse> files = fileStorageService.getUserFiles(userEmail);
        return ResponseEntity.ok(files);
    }

    // 3. Download a file as owner (Protected)
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long id,
            Authentication authentication
    ) throws MalformedURLException {
        String userEmail = authentication.getName();
        Resource resource = fileStorageService.downloadFile(id, userEmail);
        FileMetadata metadata = fileStorageService.getFileMetadata(id, userEmail);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getFileName() + "\"")
                .body(resource);
    }

    // 4. Delete file directly from disk & DB (Protected)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteFile(
            @PathVariable Long id,
            Authentication authentication
    ) throws IOException {
        String userEmail = authentication.getName();
        fileStorageService.deleteFile(id, userEmail);
        return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
    }

    // 5. Generate a public share link (Protected)
    @PostMapping("/share/{id}")
    public ResponseEntity<Map<String, String>> createShareLink(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        String token = fileStorageService.generateShareLink(id, userEmail);

        String shareUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/public/download/")
                .path(token)
                .toUriString();

        return ResponseEntity.ok(Map.of(
                "shareToken", token,
                "shareUrl", shareUrl
        ));
    }

    // 6. Revoke a public share link (Protected)
    @PostMapping("/revoke/{id}")
    public ResponseEntity<Map<String, String>> revokeShareLink(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        fileStorageService.revokeShareLink(id, userEmail);
        return ResponseEntity.ok(Map.of("message", "Share link revoked successfully"));
    }

    // 7. Public download via token (Public)
    @GetMapping("/public/download/{token}")
    public ResponseEntity<Resource> downloadPublicFile(
            @PathVariable String token
    ) throws MalformedURLException {
        Resource resource = fileStorageService.downloadPublicFile(token);
        FileMetadata metadata = fileStorageService.getFileMetadataByShareToken(token);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getFileName() + "\"")
                .body(resource);
    }

    // 8. Storage usage metrics (Protected)
    @GetMapping("/storage/usage")
    public ResponseEntity<Map<String, Object>> getStorageUsage(Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(fileStorageService.getUserStorageUsage(userEmail));
    }
}