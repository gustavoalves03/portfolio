package com.example.demo.care.web.dto;

public record CareResponse(
        Long id,
        String name,
        Integer price,
        String description,
        Integer duration,
        com.example.demo.care.domain.CareStatus status,
        Long categoryId
) {}
