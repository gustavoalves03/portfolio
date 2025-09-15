package com.example.demo.care.web;


import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.validation.Valid;
import com.example.demo.care.app.CareService;
import com.example.demo.care.web.dto.CareRequest;
import com.example.demo.care.web.dto.CareResponse;

@RestController
@RequestMapping("/api/care")
public class CareController {

    private final CareService service;
    public CareController(CareService service) { this.service = service; }

    @GetMapping
    public Page<CareResponse> list(Pageable pageable) {
        return service.list(pageable);
    }

    @PostMapping
    public CareResponse create(@RequestBody @Valid CareRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public CareResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public CareResponse update(@PathVariable Long id, @RequestBody @Valid CareRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
 }
