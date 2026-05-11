package com.luxpretty.app.proinvoice.app;

import com.luxpretty.app.proinvoice.domain.ProInvoice;
import com.luxpretty.app.proinvoice.domain.ProInvoiceStatus;
import com.luxpretty.app.proinvoice.repo.ProInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProInvoiceServiceTests {

    private ProInvoiceRepository repo;
    private ProInvoiceService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProInvoiceRepository.class);
        service = new ProInvoiceService(repo);
    }

    @Test
    void search_delegates_to_repo_with_filters() {
        Pageable pageable = PageRequest.of(0, 10);
        ProInvoice inv = buildInvoice("PRO-2026-0001");
        when(repo.search(eq(ProInvoiceStatus.PAID), eq(2026), eq("0001"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(inv)));

        Page<ProInvoice> page = service.search(ProInvoiceStatus.PAID, 2026, "0001", pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getNumberLabel()).isEqualTo("PRO-2026-0001");
    }

    @Test
    void get_returns_invoice_when_found() {
        when(repo.findById(42L)).thenReturn(Optional.of(buildInvoice("PRO-2026-0042")));
        assertThat(service.get(42L).getNumberLabel()).isEqualTo("PRO-2026-0042");
    }

    @Test
    void get_throws_404_when_not_found() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private ProInvoice buildInvoice(String number) {
        ProInvoice i = new ProInvoice();
        i.setNumberLabel(number);
        i.setIssuedAt(LocalDateTime.now());
        i.setStatus(ProInvoiceStatus.PAID);
        i.setAmountSubtotal(new BigDecimal("59.00"));
        i.setAmountTax(new BigDecimal("10.03"));
        i.setAmountTotal(new BigDecimal("69.03"));
        i.setTaxRate(new BigDecimal("17.00"));
        i.setCurrency("EUR");
        return i;
    }
}
