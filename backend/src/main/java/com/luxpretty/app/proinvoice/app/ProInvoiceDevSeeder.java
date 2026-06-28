package com.luxpretty.app.proinvoice.app;

import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.proinvoice.domain.ProInvoice;
import com.luxpretty.app.proinvoice.domain.ProInvoiceStatus;
import com.luxpretty.app.proinvoice.repo.ProInvoiceRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

@Component
@ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
public class ProInvoiceDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProInvoiceDevSeeder.class);

    private final TenantRepository tenants;
    private final ProInvoiceRepository repo;

    public ProInvoiceDevSeeder(TenantRepository tenants, ProInvoiceRepository repo) {
        this.tenants = tenants;
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Tenant tenant : tenants.findAll()) {
            try {
                TenantContext.setCurrentTenant(tenant.getSlug());
                if (repo.count() > 0) {
                    log.info("ProInvoice seed skipped for tenant {} (already populated)", tenant.getSlug());
                    continue;
                }
                seedSixMonths(tenant);
                log.info("ProInvoice seeded 6 invoices for tenant {}", tenant.getSlug());
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void seedSixMonths(Tenant tenant) {
        YearMonth start = YearMonth.now().minusMonths(5);
        for (int i = 0; i < 6; i++) {
            YearMonth ym = start.plusMonths(i);
            ProInvoice inv = new ProInvoice();
            inv.setNumberLabel(String.format("PRO-%s-%d-%04d", tenant.getSlug(), ym.getYear(), 1000 + i));
            inv.setIssuedAt(LocalDateTime.of(ym.atDay(11), LocalTime.NOON));
            inv.setPeriodStart(ym.atDay(1));
            inv.setPeriodEnd(ym.atEndOfMonth());
            inv.setAmountSubtotal(new BigDecimal("59.00"));
            inv.setAmountTax(new BigDecimal("10.03"));
            inv.setAmountTotal(new BigDecimal("69.03"));
            inv.setTaxRate(new BigDecimal("17.00"));
            inv.setCurrency("EUR");
            inv.setStatus(i == 5 ? ProInvoiceStatus.OPEN : ProInvoiceStatus.PAID);
            repo.save(inv);
        }
    }
}
