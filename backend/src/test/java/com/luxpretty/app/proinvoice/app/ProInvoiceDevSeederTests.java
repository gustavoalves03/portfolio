package com.luxpretty.app.proinvoice.app;

import com.luxpretty.app.proinvoice.domain.ProInvoice;
import com.luxpretty.app.proinvoice.repo.ProInvoiceRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProInvoiceDevSeederTests {

    private TenantRepository tenants;
    private ProInvoiceRepository repo;
    private ProInvoiceDevSeeder seeder;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        repo = mock(ProInvoiceRepository.class);
        seeder = new ProInvoiceDevSeeder(tenants, repo);
    }

    @Test
    void seeds_six_invoices_when_table_empty() throws Exception {
        Tenant t = new Tenant();
        t.setSlug("demo-salon");
        when(tenants.findAll()).thenReturn(List.of(t));
        when(repo.count()).thenReturn(0L);

        seeder.run(null);

        verify(repo, times(6)).save(any(ProInvoice.class));
    }

    @Test
    void skips_seed_when_table_already_populated() throws Exception {
        Tenant t = new Tenant();
        t.setSlug("demo-salon");
        when(tenants.findAll()).thenReturn(List.of(t));
        when(repo.count()).thenReturn(3L);

        seeder.run(null);

        verify(repo, never()).save(any(ProInvoice.class));
    }
}
