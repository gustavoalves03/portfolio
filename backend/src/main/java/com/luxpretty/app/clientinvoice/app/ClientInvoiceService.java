package com.luxpretty.app.clientinvoice.app;

import com.luxpretty.app.clientinvoice.domain.ClientInvoice;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceStatus;
import com.luxpretty.app.clientinvoice.repo.ClientInvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientInvoiceService {

    private final ClientInvoiceRepository repo;

    public ClientInvoiceService(ClientInvoiceRepository repo) {
        this.repo = repo;
    }

    public Page<ClientInvoice> searchForPro(ClientInvoiceStatus status, Integer year, String q, Pageable pageable) {
        return repo.searchForPro(status, year, q, pageable);
    }

    public Page<ClientInvoice> searchForClient(Long userId, ClientInvoiceStatus status, Integer year, Pageable pageable) {
        return repo.searchForClient(userId, status, year, pageable);
    }

    public ClientInvoice getForPro(Long id) {
        return repo.findWithLinesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client invoice not found"));
    }

    public ClientInvoice getForClient(Long id, Long userId) {
        return repo.findByIdAndClientUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client invoice not found"));
    }
}
