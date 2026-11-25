package com.finsight.service.auth;

import com.finsight.api.dto.request.LoginRequest;
import com.finsight.api.dto.request.RegisterRequest;
import com.finsight.api.dto.response.AuthResponse;
import com.finsight.api.exception.BusinessRuleException;
import com.finsight.api.exception.ResourceNotFoundException;
import com.finsight.domain.entity.AppUser;
import com.finsight.domain.entity.RefreshToken;
import com.finsight.repository.AppUserRepository;
import com.finsight.repository.RefreshTokenRepository;
import com.finsight.security.LoginAttemptService;
import com.finsight.service.privacy.AuditLogger;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;
    private final LoginAttemptService loginAttemptService;

    @Value("${finsight.jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        AppUser savedUser = registerAccount(request);
        return issueTokens(savedUser);
    }

    @Transactional
    public AppUser registerAccount(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessRuleException.conflict("An account with this email already exists");
        }

        AppUser user = AppUser.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .displayName(request.displayName().trim())
            .build();

        AppUser savedUser = appUserRepository.save(user);
        auditLogger.record(savedUser.getId(), "USER_REGISTERED", "AppUser",
            savedUser.getId().toString(), "New user registration completed");
        return savedUser;
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        if (loginAttemptService.isBlocked(email)) {
            throw new LockedException("Too many failed sign-in attempts. Try again later.");
        }

        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
            .filter(AppUser::isEnabled)
            .orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(email);
            if (user != null) {
                auditLogger.record(user.getId(), "LOGIN_FAILED", "AppUser",
                    user.getId().toString(), "Login attempt with invalid password");
            }
            throw new BadCredentialsException("Invalid credentials");
        }
        loginAttemptService.recordSuccess(email);

        // A fresh API login invalidates all previously issued refresh tokens for this user.
        refreshTokenRepository.revokeAllByUserId(user.getId());
        auditLogger.record(user.getId(), "LOGIN_SUCCESS", "AppUser",
            user.getId().toString(), "User login successful");
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken storedToken = findValidRefreshToken(rawRefreshToken);
        AppUser user = storedToken.getUser();
        if (!user.isEnabled()) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        // Rotate: the presented token can never be used again.
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        auditLogger.record(user.getId(), "TOKEN_REFRESHED", "RefreshToken",
            storedToken.getId().toString(), "Access token refreshed via refresh token rotation");
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken storedToken = findValidRefreshToken(rawRefreshToken);
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        auditLogger.record(storedToken.getUser().getId(), "LOGOUT", "RefreshToken",
            storedToken.getId().toString(), "User logout, refresh token revoked");
    }

    @Transactional(readOnly = true)
    public AppUser findUser(UUID userId) {
        return appUserRepository.findById(userId)
            .filter(AppUser::isEnabled)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Transactional
    public AppUser updateProfile(UUID userId, String displayName, String preferredCurrency) {
        AppUser user = findUser(userId);
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new BusinessRuleException("Display name must be between 1 and 100 characters");
        }
        String currency = preferredCurrency == null ? "" : preferredCurrency.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new BusinessRuleException("Currency must be a three-letter ISO code");
        }
        user.setDisplayName(name);
        user.setPreferredCurrency(currency);
        AppUser saved = appUserRepository.save(user);
        auditLogger.record(userId, "PROFILE_UPDATED", "AppUser", userId.toString(), "Profile settings updated");
        return saved;
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        AppUser user = findUser(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (newPassword.length() > 128) {
            throw new BusinessRuleException("New password must be at most 128 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId);
        auditLogger.record(userId, "PASSWORD_CHANGED", "AppUser", userId.toString(),
            "Password changed; API refresh tokens revoked");
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse issueTokens(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();
        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .tokenHash(jwtService.hashRefreshToken(rawRefreshToken))
            .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays))
            .revoked(false)
            .build();
        refreshTokenRepository.save(refreshToken);
        return new AuthResponse(accessToken, rawRefreshToken, user.getId(), user.getEmail());
    }

    private RefreshToken findValidRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        return refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(rawToken.trim()))
            .filter(rt -> !rt.isRevoked())
            .filter(rt -> rt.getExpiresAt().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
    }
}
