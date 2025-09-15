package com.fleurdecoquillage.app.category.web.mapper;

import com.fleurdecoquillage.app.category.domain.Category;
import com.fleurdecoquillage.app.category.web.dto.CategoryRequest;
import com.fleurdecoquillage.app.category.web.dto.CategoryResponse;

public class CategoryMapper {
    public static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
    }
    public static Category toEntity(CategoryRequest req) {
        Category c = new Category();
        updateEntity(c, req);
        return c;
    }
    public static void updateEntity(Category c, CategoryRequest req) {
        c.setName(req.name());
        c.setDescription(req.description());
    }
}
