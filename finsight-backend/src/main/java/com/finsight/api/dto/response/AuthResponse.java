package com.finsight.api.dto.response;

import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UUID userId,
    String email
) {}
