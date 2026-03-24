package com.prettyface.app.availability.web.dto;

public record OpeningHourResponse(
        Long id,
        Integer dayOfWeek,
        String openTime,
        String closeTime
) {}
