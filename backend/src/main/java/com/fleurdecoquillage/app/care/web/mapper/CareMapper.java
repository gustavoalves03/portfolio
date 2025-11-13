package com.fleurdecoquillage.app.care.web.mapper;

import com.fleurdecoquillage.app.care.domain.Care;
import com.fleurdecoquillage.app.care.domain.CareImage;
import com.fleurdecoquillage.app.care.web.dto.CareImageDto;
import com.fleurdecoquillage.app.care.web.dto.CareRequest;
import com.fleurdecoquillage.app.care.web.dto.CareResponse;

import java.util.List;
import java.util.stream.Collectors;

public class CareMapper {

    public static CareResponse toResponse(Care c) {
        List<CareImageDto> imageDtos = c.getImages() != null
                ? c.getImages().stream().map(CareMapper::toImageDto).collect(Collectors.toList())
                : List.of();

        return new CareResponse(
                c.getId(),
                c.getName(),
                c.getPrice(),
                c.getDescription(),
                c.getDuration(),
                c.getStatus(),
                c.getCategory() != null ? c.getCategory().getId() : null,
                imageDtos
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

    public static CareImageDto toImageDto(CareImage img) {
        // Generate URL for frontend
        String url = String.format("/api/images/cares/%d/%s",
                img.getCare().getId(),
                img.getFilename());

        return new CareImageDto(
                img.getId() != null ? String.valueOf(img.getId()) : null,
                img.getName(),
                img.getImageOrder(),
                url,
                null  // Don't send base64Data in response
        );
    }

    public static CareImage toImageEntity(CareImageDto dto, Care care) {
        CareImage img = new CareImage();
        img.setName(dto.name());
        img.setImageOrder(dto.order());
        img.setCare(care);
        // filename and filePath will be set by service after saving file
        return img;
    }
}
