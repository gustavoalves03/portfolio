package com.fleurdecoquillage.app.care.app;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.fleurdecoquillage.app.care.repo.CareRepository;
import com.fleurdecoquillage.app.care.domain.Care;
import com.fleurdecoquillage.app.care.web.dto.CareRequest;
import com.fleurdecoquillage.app.care.web.dto.CareResponse;
import com.fleurdecoquillage.app.care.web.mapper.CareMapper;
import com.fleurdecoquillage.app.category.repo.CategoryRepository;

@Service
public class CareService {

    private final CareRepository repo;
    private final CategoryRepository categoryRepository;
    public CareService(CareRepository repo, CategoryRepository categoryRepository) {
        this.repo = repo;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<CareResponse> list(Pageable pageable) {
        return repo.findAll(pageable).map(CareMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CareResponse get(Long id) {
        return repo.findById(id).map(CareMapper::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Care not found: " + id));
    }

    @Transactional
    public CareResponse create(CareRequest req) {
        Care entity = CareMapper.toEntity(req);
        var category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + req.categoryId()));
        entity.setCategory(category);
        Care saved = repo.save(entity);
        return CareMapper.toResponse(saved);
    }

    @Transactional
    public CareResponse update(Long id, CareRequest req) {
        Care c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Care not found: " + id));
        CareMapper.updateEntity(c, req);
        var category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + req.categoryId()));
        c.setCategory(category);
        return CareMapper.toResponse(repo.save(c));
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
