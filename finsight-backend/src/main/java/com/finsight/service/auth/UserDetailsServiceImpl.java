package com.finsight.service.auth;

import com.finsight.domain.entity.AppUser;
import com.finsight.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Never echo the submitted email into exception messages; they can reach logs.
        AppUser user = appUserRepository.findByEmailIgnoreCase(AuthService.normalizeEmail(email))
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return user;
    }
}
