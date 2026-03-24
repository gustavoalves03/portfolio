package com.prettyface.app.category.app;

import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.category.web.dto.CategoryRequest;
import com.prettyface.app.category.web.dto.CategoryResponse;
import com.prettyface.app.category.web.dto.DeleteCategoryResponse;
import com.prettyface.app.category.web.mapper.CategoryMapper;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tenant.app.SlugUtils;
import com.prettyface.app.tenant.repo.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private final CategoryRepository repo;
    private final CareRepository careRepository;
    private final TenantRepository tenantRepository;

    public CategoryService(CategoryRepository repo, CareRepository careRepository, TenantRepository tenantRepository) {
        this.repo = repo;
        this.careRepository = careRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(Pageable pageable) {
        return repo.findAll(pageable).map(CategoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return repo.findAll().stream().map(CategoryMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return repo.findById(id).map(CategoryMapper::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        Category saved = repo.save(CategoryMapper.toEntity(req));
        syncTenantCategories();
        return CategoryMapper.toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        CategoryMapper.updateEntity(c, req);
        CategoryResponse response = CategoryMapper.toResponse(repo.save(c));
        syncTenantCategories();
        return response;
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    @Transactional
    public DeleteCategoryResponse deleteWithReassignment(Long id, Long reassignToId) {
        repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

        long careCount = careRepository.countByCategoryId(id);

        if (careCount > 0 && reassignToId == null) {
            throw new IllegalStateException(
                    "Category has " + careCount + " care(s), reassignTo is required");
        }

        if (careCount > 0) {
            if (reassignToId.equals(id)) {
                throw new IllegalStateException("Cannot reassign to the same category");
            }
            Category target = repo.findById(reassignToId)
                    .orElseThrow(() -> new IllegalArgumentException("Target category not found: " + reassignToId));
            careRepository.reassignCategory(id, target);
        }

        repo.deleteById(id);
        syncTenantCategories();
        return new DeleteCategoryResponse((int) careCount);
    }

    private void syncTenantCategories() {
        String slug = TenantContext.getCurrentTenant();
        if (slug == null) return;

        tenantRepository.findBySlug(slug).ifPresent(tenant -> {
            List<Category> categories = repo.findAll();
            String names = categories.stream()
                    .map(Category::getName)
                    .sorted()
                    .collect(Collectors.joining(", "));
            String slugs = categories.stream()
                    .map(c -> SlugUtils.toSlug(c.getName()))
                    .sorted()
                    .collect(Collectors.joining(","));
            tenant.setCategoryNames(names.isEmpty() ? null : names);
            tenant.setCategorySlugs(slugs.isEmpty() ? null : slugs);
            tenantRepository.save(tenant);
        });
    }
}
