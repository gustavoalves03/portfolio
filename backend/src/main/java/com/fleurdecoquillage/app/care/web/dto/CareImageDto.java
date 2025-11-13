package com.fleurdecoquillage.app.care.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CareImageDto(
        Long id,
        String name,
        Integer order,
        String url,
        String base64Data
) {}
