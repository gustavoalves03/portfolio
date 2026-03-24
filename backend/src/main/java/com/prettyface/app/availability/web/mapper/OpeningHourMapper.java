package com.prettyface.app.availability.web.mapper;

import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.web.dto.OpeningHourRequest;
import com.prettyface.app.availability.web.dto.OpeningHourResponse;

import java.time.LocalTime;

public class OpeningHourMapper {

    public static OpeningHour toEntity(OpeningHourRequest req) {
        OpeningHour h = new OpeningHour();
        h.setDayOfWeek(req.dayOfWeek());
        h.setOpenTime(LocalTime.parse(req.openTime()));
        h.setCloseTime(LocalTime.parse(req.closeTime()));
        return h;
    }

    public static OpeningHourResponse toResponse(OpeningHour h) {
        return new OpeningHourResponse(
                h.getId(),
                h.getDayOfWeek(),
                h.getOpenTime().toString(),
                h.getCloseTime().toString()
        );
    }
}
