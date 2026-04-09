package com.prettyface.app.config;

import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.notification.app.NotificationDispatcher;
import com.prettyface.app.notification.domain.NotificationCategory;
import com.prettyface.app.notification.domain.NotificationType;
import com.prettyface.app.notification.domain.ReferenceType;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.tracking.domain.SalonClient;
import com.prettyface.app.tracking.repo.SalonClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class BirthdayScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BirthdayScheduler.class);

    private final TenantRepository tenantRepository;
    private final SalonClientRepository salonClientRepository;
    private final NotificationDispatcher notificationDispatcher;

    public BirthdayScheduler(TenantRepository tenantRepository,
                              SalonClientRepository salonClientRepository,
                              NotificationDispatcher notificationDispatcher) {
        this.tenantRepository = tenantRepository;
        this.salonClientRepository = salonClientRepository;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Scheduled(cron = "0 0 8 * * *") // Every day at 8:00 AM
    public void checkBirthdays() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd"));
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            try {
                TenantContext.setCurrentTenant(tenant.getSlug());
                List<SalonClient> birthdayClients = salonClientRepository.findByBirthdayMonthDay(today);

                for (SalonClient client : birthdayClients) {
                    notificationDispatcher.dispatch(
                            List.of(tenant.getOwnerId()),
                            tenant.getSlug(),
                            NotificationType.CLIENT_BIRTHDAY,
                            NotificationCategory.CLIENT,
                            client.getName() + " fête son anniversaire !",
                            client.getName() + " fête son anniversaire aujourd'hui !",
                            client.getId(),
                            ReferenceType.SALON_CLIENT
                    );
                    logger.info("Birthday notification sent for {} in tenant {}", client.getName(), tenant.getSlug());
                }
            } catch (Exception e) {
                logger.error("Birthday check failed for tenant {}: {}", tenant.getSlug(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
