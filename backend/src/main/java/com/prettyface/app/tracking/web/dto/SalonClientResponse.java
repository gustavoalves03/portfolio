package com.prettyface.app.tracking.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalonClientResponse(
        Long id,
        String name,
        String phone,
        String email,
        LocalDate dateOfBirth,
        String notes,
        Long userId,
        boolean manual,
        LocalDateTime createdAt,
        String createdByName
) {}
