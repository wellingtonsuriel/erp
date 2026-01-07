package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.enums.Role;
import com.pos_onlineshop.hybrid.services.FileStorageService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.storedFiles.StoredFile;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileStorageController {

    private final FileStorageService fileStorageService;
    private final UserAccountService userAccountService;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StoredFile> uploadFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String referenceType,
            @RequestParam(required = false) Long referenceId) {

        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            StoredFile storedFile = fileStorageService.storeFile(
                    file, user, referenceType, referenceId
            );
            return ResponseEntity.ok(storedFile);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ByteArrayResource> downloadFile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        StoredFile storedFile = fileStorageService.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        UserAccount user = userDetails != null ?
                userAccountService.findByUsername(userDetails.getUsername()).orElse(null) : null;

        if (!storedFile.canBeAccessedBy(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            byte[] data = fileStorageService.getFileContent(id);
            ByteArrayResource resource = new ByteArrayResource(data);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(storedFile.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + storedFile.getOriginalName() + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/my-files")
    @PreAuthorize("hasRole('USER')")
    public List<StoredFile> getMyFiles(@AuthenticationPrincipal UserDetails userDetails) {
        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return fileStorageService.findByUser(user);
    }

    @GetMapping("/reference/{referenceType}/{referenceId}")
    @PreAuthorize("hasRole('USER')")
    public List<StoredFile> getFilesByReference(
            @PathVariable String referenceType,
            @PathVariable Long referenceId) {
        return fileStorageService.findByReference(referenceType, referenceId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        StoredFile storedFile = fileStorageService.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!storedFile.getUploadedBy().equals(user) && !user.hasRole(Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            fileStorageService.deleteFile(id);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/storage-usage")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Long> getStorageUsage(@AuthenticationPrincipal UserDetails userDetails) {
        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long usage = fileStorageService.getUserStorageUsage(user.getId());
        return ResponseEntity.ok(usage);
    }
}