package com.example.demo.care.app;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.example.demo.care.repo.CareRepository;
import com.example.demo.care.domain.Care;
import com.example.demo.care.web.dto.CareRequest;
import com.example.demo.care.web.dto.CareResponse;
import com.example.demo.care.web.mapper.CareMapper;

@Service
public class CareService {

    private final CareRepository repo;
    public CareService(CareRepository repo) { this.repo = repo; }

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
        Care saved = repo.save(CareMapper.toEntity(req));
        return CareMapper.toResponse(saved);
    }

    @Transactional
    public CareResponse update(Long id, CareRequest req) {
        Care c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Care not found: " + id));
        CareMapper.updateEntity(c, req);
        return CareMapper.toResponse(repo.save(c));
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
