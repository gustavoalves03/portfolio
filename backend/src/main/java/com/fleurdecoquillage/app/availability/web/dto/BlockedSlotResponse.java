package com.fleurdecoquillage.app.availability.web.dto;

import java.time.LocalDate;

public record BlockedSlotResponse(
        Long id,
        LocalDate date,
        String startTime,
        String endTime,
        boolean fullDay,
        String reason
) {}
