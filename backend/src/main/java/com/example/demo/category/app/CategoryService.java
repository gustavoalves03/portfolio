package com.example.demo.category.app;

import com.example.demo.category.domain.Category;
import com.example.demo.category.repo.CategoryRepository;
import com.example.demo.category.web.dto.CategoryRequest;
import com.example.demo.category.web.dto.CategoryResponse;
import com.example.demo.category.web.mapper.CategoryMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository repo;
    public CategoryService(CategoryRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(Pageable pageable) {
        return repo.findAll(pageable).map(CategoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return repo.findById(id).map(CategoryMapper::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        Category saved = repo.save(CategoryMapper.toEntity(req));
        return CategoryMapper.toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        CategoryMapper.updateEntity(c, req);
        return CategoryMapper.toResponse(repo.save(c));
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }
}

