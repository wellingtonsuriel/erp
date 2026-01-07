package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AddRoleRequest;
import com.pos_onlineshop.hybrid.dtos.RegistrationRequest;
import com.pos_onlineshop.hybrid.dtos.UpdateUserRequest;
import com.pos_onlineshop.hybrid.enums.Role;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserAccountController {

    private final UserAccountService userAccountService;

    @PostMapping("/register")
    public ResponseEntity<UserAccount> register(@Valid @RequestBody RegistrationRequest request) {
        try {
            UserAccount user = userAccountService.registerUser(
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(user);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserAccount> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        return userAccountService.findByUsername(userDetails.getUsername())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserAccount> updateCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateUserRequest request) {
        UserAccount user = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserAccount updated = userAccountService.updateUser(user.getId(), request.toUserAccount());
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserAccount> getAllUsers() {
        return userAccountService.findAllUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAccount> getUserById(@PathVariable Long id) {
        return userAccountService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserAccount> getUsersByRole(@PathVariable Role role) {
        return userAccountService.findByRole(role);
    }

    @GetMapping("/email/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAccount> getUserByEmail(@PathVariable String email) {
        return userAccountService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> enableUser(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        userAccountService.enableUser(id, enabled);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addRole(
            @PathVariable Long id,
            @RequestBody AddRoleRequest request) {
        userAccountService.addRoleToUser(id, request.getRole());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/roles/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeRole(
            @PathVariable Long id,
            @PathVariable Role role) {
        UserAccount user = userAccountService.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.removeRole(role);
        userAccountService.updateUser(id, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getUserStatistics() {
        List<UserAccount> allUsers = userAccountService.findAllUsers();
        long activeUsers = allUsers.stream().filter(UserAccount::isEnabled).count();
        long adminCount = userAccountService.findByRole(Role.ADMIN).size();
        long cashierCount = userAccountService.findByRole(Role.CASHIER).size();
        long customerCount = userAccountService.findByRole(Role.USER).size();

        return Map.of(
                "totalUsers", allUsers.size(),
                "activeUsers", activeUsers,
                "adminCount", adminCount,
                "cashierCount", cashierCount,
                "customerCount", customerCount
        );
    }


}