package com.luxpretty.app.clientinvoice.web;

import com.luxpretty.app.clientinvoice.app.ClientInvoicePdfRenderer;
import com.luxpretty.app.clientinvoice.app.ClientInvoiceService;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceStatus;
import com.luxpretty.app.clientinvoice.web.dto.ClientInvoiceResponse;
import com.luxpretty.app.clientinvoice.web.mapper.ClientInvoiceMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pro/client-invoices")
public class ClientInvoiceController {

    private final ClientInvoiceService service;
    private final ClientInvoiceMapper mapper;
    private final ClientInvoicePdfRenderer pdfRenderer;

    public ClientInvoiceController(ClientInvoiceService service, ClientInvoiceMapper mapper,
                                   ClientInvoicePdfRenderer pdfRenderer) {
        this.service = service;
        this.mapper = mapper;
        this.pdfRenderer = pdfRenderer;
    }

    @GetMapping
    public Page<ClientInvoiceResponse> list(
            @RequestParam(required = false) ClientInvoiceStatus status,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        return service.searchForPro(status, year, q, pageable).map(mapper::toResponse);
    }

    @GetMapping("/{id}")
    public ClientInvoiceResponse get(@PathVariable Long id) {
        return mapper.toResponse(service.getForPro(id));
    }

    @GetMapping("/{id}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        var invoice = service.getForPro(id);
        byte[] body = pdfRenderer.render(invoice);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"facture-" + invoice.getNumberLabel() + ".pdf\"")
                .header("Content-Type", "application/pdf")
                .body(body);
    }
}
