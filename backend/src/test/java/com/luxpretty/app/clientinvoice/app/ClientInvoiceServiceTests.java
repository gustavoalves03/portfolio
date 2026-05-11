package com.luxpretty.app.clientinvoice.app;

import com.luxpretty.app.clientinvoice.domain.ClientInvoice;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceKind;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceStatus;
import com.luxpretty.app.clientinvoice.repo.ClientInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientInvoiceServiceTests {

    private ClientInvoiceRepository repo;
    private ClientInvoiceService service;

    @BeforeEach
    void setUp() {
        repo = mock(ClientInvoiceRepository.class);
        service = new ClientInvoiceService(repo);
    }

    @Test
    void searchForPro_delegates() {
        when(repo.searchForPro(eq(ClientInvoiceStatus.PAID), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sample(1L))));
        assertThat(service.searchForPro(ClientInvoiceStatus.PAID, 2026, null, PageRequest.of(0, 10)).getContent())
                .hasSize(1);
    }

    @Test
    void searchForClient_filters_by_user() {
        when(repo.searchForClient(eq(42L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sample(42L))));
        assertThat(service.searchForClient(42L, null, null, PageRequest.of(0, 10)).getContent())
                .hasSize(1);
    }

    @Test
    void getForClient_throws_404_when_owned_by_someone_else() {
        when(repo.findByIdAndClientUserId(7L, 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getForClient(7L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private ClientInvoice sample(Long userId) {
        ClientInvoice i = new ClientInvoice();
        i.setClientUserId(userId);
        i.setNumberLabel("DEMO-2026-0001");
        i.setIssuedAt(LocalDateTime.now());
        i.setKind(ClientInvoiceKind.NO_SHOW_FEE);
        i.setStatus(ClientInvoiceStatus.PAID);
        i.setAmountSubtotal(new BigDecimal("25.00"));
        i.setAmountTax(new BigDecimal("4.25"));
        i.setAmountTotal(new BigDecimal("29.25"));
        i.setTaxRate(new BigDecimal("17.00"));
        i.setCurrency("EUR");
        return i;
    }
}
