package com.prettyface.app.bookings.web.dto;

public record CareInfo(
    Long id,
    String name,
    Integer price,
    Integer duration
) {}
