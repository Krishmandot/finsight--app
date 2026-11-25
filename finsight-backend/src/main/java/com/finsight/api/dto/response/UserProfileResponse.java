package com.finsight.api.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(UUID id, String email, String displayName, String preferredCurrency,
                                  LocalDateTime createdAt) {
}
