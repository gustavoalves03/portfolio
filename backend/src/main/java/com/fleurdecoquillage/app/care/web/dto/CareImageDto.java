package com.fleurdecoquillage.app.care.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CareImageDto(
        String id,  // Can be UUID (new images) or Long ID as String (existing images)
        String name,
        Integer order,
        String url,
        String base64Data
) {}
