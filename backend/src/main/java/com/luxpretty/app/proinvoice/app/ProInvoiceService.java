package com.luxpretty.app.proinvoice.app;

import com.luxpretty.app.proinvoice.domain.ProInvoice;
import com.luxpretty.app.proinvoice.domain.ProInvoiceStatus;
import com.luxpretty.app.proinvoice.repo.ProInvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProInvoiceService {

    private final ProInvoiceRepository repo;

    public ProInvoiceService(ProInvoiceRepository repo) {
        this.repo = repo;
    }

    public Page<ProInvoice> search(ProInvoiceStatus status, Integer year, String q, Pageable pageable) {
        return repo.search(status, year, q, pageable);
    }

    public ProInvoice get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pro invoice not found"));
    }
}
