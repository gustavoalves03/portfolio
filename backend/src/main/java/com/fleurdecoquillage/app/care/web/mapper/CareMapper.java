package com.fleurdecoquillage.app.care.web.mapper;

import com.fleurdecoquillage.app.care.domain.Care;
import com.fleurdecoquillage.app.care.web.dto.CareRequest;
import com.fleurdecoquillage.app.care.web.dto.CareResponse;

public class CareMapper {

    public static CareResponse toResponse(Care c) {
        return new CareResponse(
                c.getId(),
                c.getName(),
                c.getPrice(),
                c.getDescription(),
                c.getDuration(),
                c.getStatus(),
                c.getCategory() != null ? c.getCategory().getId() : null
        );
    }

    public static Care toEntity(CareRequest req) {
        Care c = new Care();
        updateEntity(c, req);
        return c;
    }

    public static void updateEntity(Care c, CareRequest req) {
        c.setName(req.name());
        c.setPrice(req.price());
        c.setDescription(req.description());
        c.setDuration(req.duration());
        c.setStatus(req.status());
    }
}
