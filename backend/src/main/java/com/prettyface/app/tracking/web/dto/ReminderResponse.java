package com.prettyface.app.tracking.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReminderResponse(
        Long id,
        Long userId,
        Long careId,
        String careName,
        LocalDate recommendedDate,
        String message,
        boolean sent,
        LocalDateTime createdAt,
        String createdByName
) {}
