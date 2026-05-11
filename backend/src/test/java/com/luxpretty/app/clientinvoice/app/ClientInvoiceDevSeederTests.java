package com.luxpretty.app.clientinvoice.app;

import com.luxpretty.app.clientinvoice.domain.ClientInvoice;
import com.luxpretty.app.clientinvoice.repo.ClientInvoiceRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClientInvoiceDevSeederTests {

    private TenantRepository tenants;
    private ClientInvoiceRepository repo;
    private ClientInvoiceDevSeeder seeder;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        repo = mock(ClientInvoiceRepository.class);
        seeder = new ClientInvoiceDevSeeder(tenants, repo);
    }

    @Test
    void seeds_four_invoices_when_table_empty() throws Exception {
        Tenant t = new Tenant();
        t.setSlug("demo-salon");
        when(tenants.findAll()).thenReturn(List.of(t));
        when(repo.count()).thenReturn(0L);

        seeder.run(null);

        // 4 invoices: 2 PAID + 1 REFUNDED + 1 FAILED, each with one line
        verify(repo, times(4)).save(any(ClientInvoice.class));
    }

    @Test
    void skips_seed_when_table_already_populated() throws Exception {
        Tenant t = new Tenant();
        t.setSlug("demo-salon");
        when(tenants.findAll()).thenReturn(List.of(t));
        when(repo.count()).thenReturn(2L);

        seeder.run(null);

        verify(repo, never()).save(any(ClientInvoice.class));
    }
}
