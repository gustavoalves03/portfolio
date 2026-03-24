package com.prettyface.app.category.web;

import com.prettyface.app.category.app.CategoryService;
import com.prettyface.app.category.web.dto.CategoryRequest;
import com.prettyface.app.category.web.dto.CategoryResponse;
import com.prettyface.app.category.web.dto.DeleteCategoryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pro/categories")
public class ProCategoryController {

    private final CategoryService service;

    public ProCategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return service.listAll();
    }

    @PostMapping
    public CategoryResponse create(@RequestBody @Valid CategoryRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @RequestBody @Valid CategoryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public DeleteCategoryResponse delete(@PathVariable Long id,
                                         @RequestParam(required = false) Long reassignTo) {
        return service.deleteWithReassignment(id, reassignTo);
    }
}
