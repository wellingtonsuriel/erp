package com.pos_onlineshop.hybrid.storedFiles;

import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByFileName(String fileName);

    List<StoredFile> findByUploadedBy(UserAccount uploadedBy);

    List<StoredFile> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    List<StoredFile> findByIsPublic(boolean isPublic);

    @Query("SELECT sf.contentType, COUNT(sf), SUM(sf.fileSize) FROM StoredFile sf " +
            "GROUP BY sf.contentType")
    List<Object[]> getStorageStatsByContentType();

    @Query("SELECT SUM(sf.fileSize) FROM StoredFile sf WHERE sf.uploadedBy.id = :userId")
    Long getTotalStorageUsedByUser(Long userId);
}
