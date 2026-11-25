package com.finsight.ui.support;

import com.finsight.domain.entity.AppUser;
import com.finsight.service.auth.AuthService;
import com.vaadin.flow.spring.security.AuthenticationContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * The signed-in user for Vaadin views. Only the id is taken from the session principal; profile data is
 * always re-read so changes made in Settings show up immediately.
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final AuthenticationContext authenticationContext;
    private final AuthService authService;

    public UUID id() {
        return authenticationContext.getAuthenticatedUser(AppUser.class)
            .map(AppUser::getId)
            .orElseThrow(() -> new AccessDeniedException("Not signed in"));
    }

    public AppUser profile() {
        return authService.findUser(id());
    }

    public String currency() {
        return profile().getPreferredCurrency();
    }

    public void logout() {
        authenticationContext.logout();
    }
}
