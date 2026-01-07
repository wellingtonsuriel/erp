package com.pos_onlineshop.hybrid.storedFiles;

import com.pos_onlineshop.hybrid.enums.Role;
import com.pos_onlineshop.hybrid.enums.StorageType;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stored_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "storage_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StorageType storageType = StorageType.LOCAL;

    @ManyToOne
    @JoinColumn(name = "uploaded_by")
    private UserAccount uploadedBy;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "uploaded_at")
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "is_public")
    @Builder.Default
    private boolean isPublic = false;

    public String getFullPath() {
        return storageType == StorageType.LOCAL ?
                "uploads/" + filePath :
                "cloud://" + filePath;
    }

    public boolean canBeAccessedBy(UserAccount user) {
        if (isPublic) return true;
        if (uploadedBy != null && uploadedBy.equals(user)) return true;
        return user != null && user.hasRole(Role.ADMIN);
    }
}