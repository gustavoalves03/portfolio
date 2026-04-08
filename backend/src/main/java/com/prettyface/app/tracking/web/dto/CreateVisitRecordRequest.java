package com.prettyface.app.tracking.web.dto;

import java.time.LocalDate;

public record CreateVisitRecordRequest(
        Long bookingId,
        Long careId,
        String careName,
        LocalDate visitDate,
        String practitionerNotes,
        String productsUsed
) {}
