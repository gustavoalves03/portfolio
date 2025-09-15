package com.example.demo.category.web;

import com.example.demo.category.app.CategoryService;
import com.example.demo.category.web.dto.CategoryRequest;
import com.example.demo.category.web.dto.CategoryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    public Page<CategoryResponse> list(Pageable pageable) { return service.list(pageable); }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public CategoryResponse create(@RequestBody @Valid CategoryRequest req) { return service.create(req); }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @RequestBody @Valid CategoryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }
}

