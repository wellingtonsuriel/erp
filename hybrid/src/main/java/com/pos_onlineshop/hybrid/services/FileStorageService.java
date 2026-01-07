package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.enums.StorageType;
import com.pos_onlineshop.hybrid.storedFiles.StoredFile;
import com.pos_onlineshop.hybrid.storedFiles.StoredFileRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FileStorageService {

    private final StoredFileRepository fileRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.storage.type:LOCAL}")
    private String storageType;

    private Path uploadPath;
    private Map<String, byte[]> memoryStorage = new HashMap<>();

    @PostConstruct
    public void init() {
        if ("LOCAL".equals(storageType)) {
            this.uploadPath = Paths.get(uploadDir);
            try {
                Files.createDirectories(uploadPath);
            } catch (IOException e) {
                throw new RuntimeException("Could not create upload directory", e);
            }
        }
    }

    public StoredFile storeFile(MultipartFile file, UserAccount uploadedBy,
                                String referenceType, Long referenceId) throws IOException {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileName = generateUniqueFileName(originalName);

        StoredFile storedFile = StoredFile.builder()
                .fileName(fileName)
                .originalName(originalName)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .filePath(fileName)
                .storageType(StorageType.valueOf(storageType))
                .uploadedBy(uploadedBy)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();

        if ("LOCAL".equals(storageType)) {
            Path targetLocation = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } else if ("MEMORY".equals(storageType)) {
            memoryStorage.put(fileName, file.getBytes());
        }

        return fileRepository.save(storedFile);
    }

    public byte[] getFileContent(Long fileId) throws IOException {
        StoredFile storedFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));

        if (storedFile.getStorageType() == StorageType.LOCAL) {
            Path filePath = uploadPath.resolve(storedFile.getFilePath());
            return Files.readAllBytes(filePath);
        } else if (storedFile.getStorageType() == StorageType.MEMORY) {
            return memoryStorage.get(storedFile.getFileName());
        }

        throw new RuntimeException("Unsupported storage type: " + storedFile.getStorageType());
    }

    @Transactional
    public void deleteFile(Long fileId) throws IOException {
        StoredFile storedFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));

        if (storedFile.getStorageType() == StorageType.LOCAL) {
            Path filePath = uploadPath.resolve(storedFile.getFilePath());
            Files.deleteIfExists(filePath);
        } else if (storedFile.getStorageType() == StorageType.MEMORY) {
            memoryStorage.remove(storedFile.getFileName());
        }

        fileRepository.delete(storedFile);
        log.info("Deleted file: {}", storedFile.getFileName());
    }

    public Optional<StoredFile> findById(Long id) {
        return fileRepository.findById(id);
    }

    public List<StoredFile> findByUser(UserAccount user) {
        return fileRepository.findByUploadedBy(user);
    }

    public List<StoredFile> findByReference(String referenceType, Long referenceId) {
        return fileRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId);
    }

    public Long getUserStorageUsage(Long userId) {
        Long usage = fileRepository.getTotalStorageUsedByUser(userId);
        return usage != null ? usage : 0L;
    }

    private String generateUniqueFileName(String originalName) {
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex);
        }
        return UUID.randomUUID().toString() + extension;
    }
}