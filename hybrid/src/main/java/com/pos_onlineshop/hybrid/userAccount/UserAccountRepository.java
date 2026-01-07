package com.pos_onlineshop.hybrid.userAccount;

import com.pos_onlineshop.hybrid.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<UserAccount> findByEnabled(boolean enabled);

    @Query("SELECT u FROM UserAccount u JOIN u.roles r WHERE r = :role")
    List<UserAccount> findByRole(Role role);
}