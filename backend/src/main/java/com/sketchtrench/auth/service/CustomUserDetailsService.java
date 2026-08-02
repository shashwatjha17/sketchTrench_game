package com.sketchtrench.auth.service;

import com.sketchtrench.user.entity.User;
import com.sketchtrench.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges our {@link User} entity to Spring Security's login machinery. During
 * {@code POST /api/auth/login}, Spring's {@code DaoAuthenticationProvider} calls
 * {@code loadUserByUsername(email)} to fetch credentials for the password check.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findWithRolesByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account with email " + email));

        if (user.isBanned()) {
            throw new UsernameNotFoundException("This account is banned");
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password(user.getPassword())
                .authorities(user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase()))
                        .toArray(SimpleGrantedAuthority[]::new))
                .build();
    }
}
