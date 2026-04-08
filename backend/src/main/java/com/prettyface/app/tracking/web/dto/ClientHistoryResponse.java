package com.prettyface.app.tracking.web.dto;

import java.util.List;

public record ClientHistoryResponse(
        String clientName,
        String clientEmail,
        ClientProfileResponse profile,
        List<VisitRecordResponse> visits,
        List<ReminderResponse> reminders
) {}
