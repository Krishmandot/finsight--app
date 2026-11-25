package com.finsight.api.controller;

import com.finsight.api.dto.request.ChangePasswordRequest;
import com.finsight.api.dto.request.ProfileRequest;
import com.finsight.api.dto.response.UserProfileResponse;
import com.finsight.domain.entity.AppUser;
import com.finsight.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping
    public UserProfileResponse me(@AuthenticationPrincipal AppUser user) {
        return toResponse(authService.findUser(user.getId()));
    }

    @PutMapping
    public UserProfileResponse update(@AuthenticationPrincipal AppUser user, @Valid @RequestBody ProfileRequest request) {
        return toResponse(authService.updateProfile(user.getId(), request.displayName(), request.preferredCurrency()));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AppUser user, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getId(), request.currentPassword(), request.newPassword());
    }

    private static UserProfileResponse toResponse(AppUser user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(),
            user.getPreferredCurrency(), user.getCreatedAt());
    }
}
