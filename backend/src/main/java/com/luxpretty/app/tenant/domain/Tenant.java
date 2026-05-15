package com.luxpretty.app.tenant.domain;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "TENANTS", uniqueConstraints = {
        @UniqueConstraint(name = "UK_TENANT_SLUG", columnNames = "slug"),
        @UniqueConstraint(name = "UK_TENANT_OWNER", columnNames = "owner_id")
})
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "name")
    private String name;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "description", columnDefinition = "CLOB")
    private String description;

    @Column(name = "logo_path", length = 500)
    private String logoPath;

    @Column(name = "hero_image_path", length = 500)
    private String heroImagePath;

    @Column(name = "category_names", length = 1000)
    private String categoryNames;

    @Column(name = "category_slugs", length = 1000)
    private String categorySlugs;

    @Column(name = "address_street", length = 255)
    private String addressStreet;

    @Column(name = "address_postal_code", length = 10)
    private String addressPostalCode;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Column(name = "address_country", length = 2)
    private String addressCountry;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "siret", length = 14)
    private String siret;

    @Builder.Default
    @Column(name = "employees_enabled")
    private Boolean employeesEnabled = false;

    @Builder.Default
    @Column(name = "annual_leave_days")
    private Integer annualLeaveDays = 25;

    @Builder.Default
    @Column(name = "closed_on_holidays")
    private Boolean closedOnHolidays = true;

    @Builder.Default
    @Column(name = "buffer_minutes")
    private Integer bufferMinutes = 0;

    @Builder.Default
    @Column(name = "min_advance_minutes")
    private Integer minAdvanceMinutes = 120; // 2h minimum before booking

    @Builder.Default
    @Column(name = "max_advance_days")
    private Integer maxAdvanceDays = 90; // 3 months max

    @Builder.Default
    @Column(name = "max_client_hours_per_day")
    private Integer maxClientHoursPerDay = 8;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Stripe subscription state (PR1) ──
    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 32)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.VITRINE_FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 32)
    @Builder.Default
    private SubscriptionTier subscriptionTier = SubscriptionTier.VITRINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_billing", nullable = false, length = 16)
    @Builder.Default
    private SubscriptionBilling subscriptionBilling = SubscriptionBilling.FREE;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "trial_end")
    private LocalDateTime trialEnd;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
