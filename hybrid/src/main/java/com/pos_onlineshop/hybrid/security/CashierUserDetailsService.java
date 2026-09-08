package com.pos_onlineshop.hybrid.security;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.services.CashierService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The second, equally-real half of this application's authentication model: POS/back-office
 * staff are {@link Cashier} rows, not {@link com.pos_onlineshop.hybrid.userAccount.UserAccount}
 * rows (that entity is for online-shop customers - see its own class comment history and
 * UserAccountService's class comment). Bridging Cashier into the same JWT
 * (JwtService/JwtAuthenticationFilter) pipeline UserAccount already used - rather than merging
 * the two entities, which would conflate two genuinely distinct principal types (a customer
 * should never gain till/back-office authority, and vice versa) - is what makes
 * CashierController.authenticateCashier/authenticateByPin able to issue a real, verifiable
 * bearer token instead of the empty one the frontend previously silently tolerated.
 *
 * Authority mapping mirrors two existing, already-used conventions in this codebase rather than
 * inventing a third: ROLE_CASHIER/ROLE_SUPERVISOR/ROLE_MANAGER/ROLE_ADMIN are the exact role
 * names several controllers already check (e.g. CashierController's own
 * hasAnyRole('CASHIER','SUPERVISOR','MANAGER','ADMIN'), and the hasRole('ADMIN') or
 * hasRole('CASHIER') checks on CustomersController/TaxController/OrderController/
 * SalesController) - a cumulative role hierarchy (ADMIN implies MANAGER implies SUPERVISOR
 * implies CASHIER) rather than a single role, since a more senior till role should not lose
 * access a junior one has. Individual Permission grants (see CashierService.hasPermission) are
 * exposed as unprefixed authorities, the same pattern AccountingPermission already uses for
 * UserAccount - see AccountingPermission's class comment.
 */
@Service
@RequiredArgsConstructor
public class CashierUserDetailsService implements UserDetailsService {

    private final CashierRepository cashierRepository;
    private final CashierService cashierService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Cashier cashier = cashierRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Cashier not found: " + username));
        return new User(cashier.getUsername(), cashier.getPassword(), cashier.isActive(), true, true, true,
                authoritiesFor(cashier));
    }

    /** Public so CashierController can build the identical authority set for the JWT it issues
     * at login time, without a second round-trip through loadUserByUsername. */
    public List<GrantedAuthority> authoritiesFor(Cashier cashier) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_CASHIER"));
        if (cashier.getRole() == CashierRole.SUPERVISOR || cashier.getRole() == CashierRole.MANAGER
                || cashier.getRole() == CashierRole.ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
        }
        if (cashier.getRole() == CashierRole.MANAGER || cashier.getRole() == CashierRole.ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        }
        if (cashier.getRole() == CashierRole.ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        cashierService.getEffectivePermissions(cashier)
                .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.name())));
        return authorities;
    }
}
