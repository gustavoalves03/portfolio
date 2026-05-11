package com.luxpretty.app.clientinvoice.app;

import com.luxpretty.app.clientinvoice.domain.ClientInvoice;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceKind;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceLine;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceStatus;
import com.luxpretty.app.clientinvoice.repo.ClientInvoiceRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("dev")
public class ClientInvoiceDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClientInvoiceDevSeeder.class);

    private final TenantRepository tenants;
    private final ClientInvoiceRepository repo;

    public ClientInvoiceDevSeeder(TenantRepository tenants, ClientInvoiceRepository repo) {
        this.tenants = tenants;
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Tenant tenant : tenants.findAll()) {
            try {
                TenantContext.setCurrentTenant(tenant.getSlug());
                if (repo.count() > 0) {
                    log.info("ClientInvoice seed skipped for {}", tenant.getSlug());
                    continue;
                }
                seedSample(tenant);
                log.info("ClientInvoice seeded 4 invoices for {}", tenant.getSlug());
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void seedSample(Tenant tenant) {
        List<ClientInvoiceStatus> statuses = List.of(
                ClientInvoiceStatus.PAID,
                ClientInvoiceStatus.PAID,
                ClientInvoiceStatus.REFUNDED,
                ClientInvoiceStatus.FAILED
        );

        int idx = 1;
        for (ClientInvoiceStatus st : statuses) {
            ClientInvoice inv = new ClientInvoice();
            inv.setNumberLabel(String.format("%s-2026-%04d", tenant.getSlug(), idx));
            inv.setIssuedAt(LocalDateTime.now().minusDays(idx * 3L));
            inv.setKind(ClientInvoiceKind.NO_SHOW_FEE);
            inv.setStatus(st);
            inv.setAmountSubtotal(new BigDecimal("25.00"));
            inv.setAmountTax(new BigDecimal("4.25"));
            inv.setAmountTotal(new BigDecimal("29.25"));
            inv.setTaxRate(new BigDecimal("17.00"));
            inv.setCurrency("EUR");

            ClientInvoiceLine line = new ClientInvoiceLine();
            line.setDescription("Frais de non-présentation — soin du visage");
            line.setQuantity(new BigDecimal("1.00"));
            line.setUnitPriceHt(new BigDecimal("25.00"));
            line.setTotalHt(new BigDecimal("25.00"));
            inv.addLine(line);

            repo.save(inv);
            idx++;
        }
    }
}
