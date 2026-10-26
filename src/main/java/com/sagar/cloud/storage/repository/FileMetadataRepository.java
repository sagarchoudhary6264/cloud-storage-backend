package com.sagar.cloud.storage.repository;

import com.sagar.cloud.storage.entity.FileMetadata;
import com.sagar.cloud.storage.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    // Retained for direct lookups regardless of trash state
    List<FileMetadata> findByUser(User user);

    Optional<FileMetadata> findByIdAndUser(Long id, User user);

    // Active (non-trashed) file queries using explicit JPQL
    @Query("SELECT f FROM FileMetadata f WHERE f.user = :user AND f.isDeleted = false")
    List<FileMetadata> findByUserAndIsDeletedFalse(@Param("user") User user);

    @Query("SELECT f FROM FileMetadata f WHERE f.id = :id AND f.user = :user AND f.isDeleted = false")
    Optional<FileMetadata> findByIdAndUserAndIsDeletedFalse(@Param("id") Long id, @Param("user") User user);

    // Trashed file query using explicit JPQL
    @Query("SELECT f FROM FileMetadata f WHERE f.user = :user AND f.isDeleted = true")
    List<FileMetadata> findByUserAndIsDeletedTrue(@Param("user") User user);

    // Public share lookup
    Optional<FileMetadata> findByShareToken(String shareToken);

    // Quota query excluding soft-deleted files
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileMetadata f WHERE f.user = :user AND f.isDeleted = false")
    Long getTotalStorageUsedByUser(@Param("user") User user);
}