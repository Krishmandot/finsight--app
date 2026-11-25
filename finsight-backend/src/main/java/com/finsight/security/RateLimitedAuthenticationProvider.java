package com.finsight.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Username/password provider used by the Vaadin form login. Adds per-account throttling on top of the
 * standard BCrypt check.
 */
public class RateLimitedAuthenticationProvider extends DaoAuthenticationProvider {

    private final LoginAttemptService loginAttemptService;

    public RateLimitedAuthenticationProvider(UserDetailsService userDetailsService,
                                             LoginAttemptService loginAttemptService) {
        super(userDetailsService);
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        if (loginAttemptService.isBlocked(email)) {
            throw new LockedException("Too many failed sign-in attempts. Try again later.");
        }
        try {
            Authentication result = super.authenticate(authentication);
            loginAttemptService.recordSuccess(email);
            return result;
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(email);
            throw ex;
        }
    }
}
