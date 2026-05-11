package com.luxpretty.app.clientinvoice.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.clientinvoice.app.ClientInvoicePdfRenderer;
import com.luxpretty.app.clientinvoice.app.ClientInvoiceService;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceStatus;
import com.luxpretty.app.clientinvoice.web.dto.ClientInvoiceResponse;
import com.luxpretty.app.clientinvoice.web.mapper.ClientInvoiceMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/invoices")
public class MyInvoiceController {

    private final ClientInvoiceService service;
    private final ClientInvoiceMapper mapper;
    private final ClientInvoicePdfRenderer pdfRenderer;

    public MyInvoiceController(ClientInvoiceService service, ClientInvoiceMapper mapper,
                               ClientInvoicePdfRenderer pdfRenderer) {
        this.service = service;
        this.mapper = mapper;
        this.pdfRenderer = pdfRenderer;
    }

    @GetMapping
    public Page<ClientInvoiceResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ClientInvoiceStatus status,
            @RequestParam(required = false) Integer year,
            Pageable pageable
    ) {
        return service.searchForClient(principal.getId(), status, year, pageable).map(mapper::toResponse);
    }

    @GetMapping("/{id}")
    public ClientInvoiceResponse get(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return mapper.toResponse(service.getForClient(id, principal.getId()));
    }

    @GetMapping("/{id}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> pdf(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        var invoice = service.getForClient(id, principal.getId());
        byte[] body = pdfRenderer.render(invoice);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"facture-" + invoice.getNumberLabel() + ".pdf\"")
                .header("Content-Type", "application/pdf")
                .body(body);
    }
}
