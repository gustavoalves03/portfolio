package com.prettyface.app.tracking.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record VisitRecordResponse(
        Long id,
        Long clientProfileId,
        Long bookingId,
        Long careId,
        String careName,
        LocalDate visitDate,
        String practitionerNotes,
        String productsUsed,
        Integer satisfactionScore,
        String satisfactionComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String updatedByName,
        List<VisitPhotoResponse> photos
) {}
