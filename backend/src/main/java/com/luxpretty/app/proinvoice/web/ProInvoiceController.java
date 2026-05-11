package com.luxpretty.app.proinvoice.web;

import com.luxpretty.app.proinvoice.app.ProInvoiceService;
import com.luxpretty.app.proinvoice.domain.ProInvoiceStatus;
import com.luxpretty.app.proinvoice.web.dto.ProInvoiceResponse;
import com.luxpretty.app.proinvoice.web.mapper.ProInvoiceMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pro/invoices")
public class ProInvoiceController {

    private final ProInvoiceService service;
    private final ProInvoiceMapper mapper;

    public ProInvoiceController(ProInvoiceService service, ProInvoiceMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<ProInvoiceResponse> list(
            @RequestParam(required = false) ProInvoiceStatus status,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        return service.search(status, year, q, pageable).map(mapper::toResponse);
    }

    @GetMapping("/{id}")
    public ProInvoiceResponse get(@PathVariable Long id) {
        return mapper.toResponse(service.get(id));
    }
}
