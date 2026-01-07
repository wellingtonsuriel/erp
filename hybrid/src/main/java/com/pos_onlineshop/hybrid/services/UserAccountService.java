package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.enums.Role;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserAccountService implements UserDetailsService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CartService cartService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true, true, true,
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList())
        );
    }

    public UserAccount registerUser(String username, String password, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists: " + username);
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists: " + email);
        }

        UserAccount user = UserAccount.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .enabled(true)
                .build();

        user.addRole(Role.USER);
        UserAccount savedUser = userRepository.save(user);

        cartService.createCart(savedUser);

        log.info("Registered new user: {}", username);
        return savedUser;
    }

    public Optional<UserAccount> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<UserAccount> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<UserAccount> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<UserAccount> findAllUsers() {
        return userRepository.findAll();
    }

    public List<UserAccount> findByRole(Role role) {
        return userRepository.findByRole(role);
    }

    public UserAccount updateUser(Long id, UserAccount userDetails) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setEmail(userDetails.getEmail());
                    if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
                        user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
                    }
                    return userRepository.save(user);
                })
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public void enableUser(Long id, boolean enabled) {
        UserAccount user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        user.setEnabled(enabled);
        userRepository.save(user);
        log.info("User {} enabled status changed to: {}", user.getUsername(), enabled);
    }

    public void addRoleToUser(Long userId, Role role) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        user.addRole(role);
        userRepository.save(user);
        log.info("Added role {} to user {}", role, user.getUsername());
    }
}
