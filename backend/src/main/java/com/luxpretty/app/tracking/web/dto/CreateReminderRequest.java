package com.luxpretty.app.tracking.web.dto;

import java.time.LocalDate;

public record CreateReminderRequest(
        Long careId,
        String careName,
        LocalDate recommendedDate,
        String message
) {}
