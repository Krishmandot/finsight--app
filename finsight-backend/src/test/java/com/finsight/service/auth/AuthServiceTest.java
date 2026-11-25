package com.finsight.service.auth;

import com.finsight.api.dto.request.LoginRequest;
import com.finsight.api.dto.request.RegisterRequest;
import com.finsight.api.dto.response.AuthResponse;
import com.finsight.domain.entity.AppUser;
import com.finsight.domain.entity.RefreshToken;
import com.finsight.repository.AppUserRepository;
import com.finsight.repository.RefreshTokenRepository;
import com.finsight.service.privacy.AuditLogger;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private com.finsight.security.LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setRefreshTokenExpiry() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
    }

    @Test
    void registerCreatesUserAndReturnsTokens() {
        UUID userId = UUID.randomUUID();
        RegisterRequest request = new RegisterRequest("person@example.com", "Password123", "Person");
        AppUser savedUser = AppUser.builder()
            .id(userId)
            .email(request.email())
            .passwordHash("encoded-password")
            .displayName(request.displayName())
            .build();

        when(appUserRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(jwtService.hashRefreshToken("refresh-token")).thenReturn("encoded-refresh-token");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(savedUser);
        when(jwtService.generateAccessToken(savedUser)).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertEquals(userId, response.userId());
        assertEquals(request.email(), response.email());
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertEquals(savedUser, refreshTokenCaptor.getValue().getUser());
        assertEquals("encoded-refresh-token", refreshTokenCaptor.getValue().getTokenHash());
        assertFalse(refreshTokenCaptor.getValue().isRevoked());
        assertTrue(refreshTokenCaptor.getValue().getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void registerAccountCreatesUserWithoutIssuingApiTokens() {
        RegisterRequest request = new RegisterRequest("person@example.com", "Password123", "Person");
        AppUser savedUser = AppUser.builder()
            .id(UUID.randomUUID())
            .email(request.email())
            .passwordHash("encoded-password")
            .displayName(request.displayName())
            .build();

        when(appUserRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(savedUser);

        AppUser result = authService.registerAccount(request);

        assertEquals(savedUser, result);
        assertNotNull(result.getId());
        verifyNoInteractions(jwtService, refreshTokenRepository);
    }

    @Test
    void loginAuthenticatesExistingUserAndRotatesRefreshTokens() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder()
            .id(userId)
            .email("person@example.com")
            .passwordHash("encoded-password")
            .displayName("Person")
            .build();
        LoginRequest request = new LoginRequest(user.getEmail(), "Password123");

        when(appUserRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertEquals(userId, response.userId());
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }
}