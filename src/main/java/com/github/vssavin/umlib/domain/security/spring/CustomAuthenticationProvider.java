package com.github.vssavin.umlib.domain.security.spring;

import com.github.vssavin.umlib.config.UmConfig;
import com.github.vssavin.umlib.domain.security.SecureService;
import com.github.vssavin.umlib.domain.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link org.springframework.security.authentication.AuthenticationProvider} implementation that
 * attempts to log in the user whose password has been encrypted.
 *
 * @author vssavin on 18.12.2021
 */
@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userService;
    private final PasswordEncoder passwordEncoder;
    private final SecureService secureService;

    @Autowired
    public CustomAuthenticationProvider(UserService userService, PasswordEncoder passwordEncoder,
                                        UmConfig umConfig) {
        this.secureService = umConfig.getAuthService();
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Object credentials = authentication.getCredentials();
        Object userName = authentication.getPrincipal();
        if (credentials != null) {
            UserDetails user = userService.loadUserByUsername(userName.toString());
            if (user != null) {
                checkUserDetails(user);

                Object details = authentication.getDetails();
                String addr = "";
                if (details instanceof WebAuthenticationDetails) {
                    addr = ((WebAuthenticationDetails) details).getRemoteAddress();
                }

                String password = secureService.decrypt(credentials.toString(), secureService.getPrivateKey(addr));
                if (passwordEncoder.matches(password, user.getPassword())) {
                    List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
                    return new CustomUsernamePasswordAuthenticationToken(authentication.getPrincipal(),
                            password, authorities);
                } else {
                    throw new BadCredentialsException("Authentication failed");
                }

            } else {
                return authentication;
            }

        } else {
            return authentication;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.isAssignableFrom(CustomUsernamePasswordAuthenticationToken.class);
    }
    
    private void checkUserDetails(UserDetails userDetails) {
        if (!userDetails.isAccountNonExpired()) {
            throw new AccountExpiredException("Account is expired!");
        }
        if (!userDetails.isAccountNonLocked()) {
            throw new LockedException("Account is locked!");
        }
        if (!userDetails.isEnabled()) {
            throw new DisabledException("Account is disabled!");
        }
    }
}