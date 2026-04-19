package com.prettyface.app.test;

import com.prettyface.app.auth.TokenService;
import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.bookings.repo.CareBookingRepository;
import com.prettyface.app.bookings.repo.ClientBookingHistoryRepository;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.multitenancy.TenantSchemaManager;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Seed + reset endpoints used by the Playwright full-stack smoke tests.
 *
 * <p>Only active under the {@code smoke-test} Spring profile. The
 * {@code @Profile("smoke-test")} annotation is the safety net that keeps this
 * controller out of dev, staging and production.
 */
@RestController
@RequestMapping("/api/test")
@Profile("smoke-test")
public class SmokeTestSeedController {

    private static final Logger logger = LoggerFactory.getLogger(SmokeTestSeedController.class);

    private static final String SALON_SLUG = "beaute-du-regard";
    private static final String SALON_NAME = "Beauté du Regard";
    private static final String PRO_EMAIL = "pro-smoke@test.com";
    private static final String PRO_NAME = "Pro Smoke";
    private static final String CLIENT_EMAIL = "marie-smoke@test.com";
    private static final String CLIENT_NAME = "Marie Smoke";
    private static final String DEFAULT_PASSWORD = "Smoke1234!";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final CategoryRepository categoryRepository;
    private final CareRepository careRepository;
    private final OpeningHourRepository openingHourRepository;
    private final CareBookingRepository careBookingRepository;
    private final ClientBookingHistoryRepository clientBookingHistoryRepository;
    private final TenantSchemaManager tenantSchemaManager;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public SmokeTestSeedController(UserRepository userRepository,
                                   TenantRepository tenantRepository,
                                   CategoryRepository categoryRepository,
                                   CareRepository careRepository,
                                   OpeningHourRepository openingHourRepository,
                                   CareBookingRepository careBookingRepository,
                                   ClientBookingHistoryRepository clientBookingHistoryRepository,
                                   TenantSchemaManager tenantSchemaManager,
                                   PasswordEncoder passwordEncoder,
                                   TokenService tokenService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.categoryRepository = categoryRepository;
        this.careRepository = careRepository;
        this.openingHourRepository = openingHourRepository;
        this.careBookingRepository = careBookingRepository;
        this.clientBookingHistoryRepository = clientBookingHistoryRepository;
        this.tenantSchemaManager = tenantSchemaManager;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /**
     * Idempotent seed: ensures one pro, one client, one tenant and one active
     * care exist. Returns the ids + JWTs needed by Playwright to drive the UI.
     *
     * <p><b>Not</b> {@code @Transactional}: Hibernate resolves the current
     * tenant identifier when the transaction opens, so we need each
     * repository save to run its own transaction after we switch
     * {@link TenantContext} — otherwise tenant writes land in the default
     * application schema instead of the tenant's own schema.
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed() {
        logger.info("[smoke] /api/test/seed invoked");

        TenantContext.clear();

        User pro = userRepository.findByEmail(PRO_EMAIL).orElseGet(() ->
                userRepository.save(User.builder()
                        .name(PRO_NAME)
                        .email(PRO_EMAIL)
                        .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                        .provider(AuthProvider.LOCAL)
                        .role(Role.PRO)
                        .emailVerified(true)
                        .tenantSlug(SALON_SLUG)
                        .build()));

        // Make sure the PRO user is linked to the salon slug (idempotence on
        // reruns where the user already exists with a different tenantSlug).
        if (pro.getTenantSlug() == null || !SALON_SLUG.equals(pro.getTenantSlug())) {
            pro.setTenantSlug(SALON_SLUG);
            pro = userRepository.save(pro);
        }

        User client = userRepository.findByEmail(CLIENT_EMAIL).orElseGet(() ->
                userRepository.save(User.builder()
                        .name(CLIENT_NAME)
                        .email(CLIENT_EMAIL)
                        .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                        .provider(AuthProvider.LOCAL)
                        .role(Role.USER)
                        .emailVerified(true)
                        .build()));

        Tenant tenant = tenantRepository.findBySlug(SALON_SLUG).orElse(null);
        if (tenant == null) {
            tenant = tenantRepository.save(Tenant.builder()
                    .slug(SALON_SLUG)
                    .name(SALON_NAME)
                    .ownerId(pro.getId())
                    .status(TenantStatus.ACTIVE)
                    .build());
        }
        // Provision + migrate are both idempotent on H2; the migrate step
        // adds columns that the base provisioning doesn't create
        // (e.g. OPENING_HOURS.EMPLOYEE_ID, CARE.DISPLAY_ORDER, …).
        tenantSchemaManager.provisionSchema(SALON_SLUG);
        tenantSchemaManager.migrateSchema(SALON_SLUG);

        Long careId;
        TenantContext.setCurrentTenant(SALON_SLUG);
        try {
            Category category = categoryRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(() -> {
                        Category c = new Category();
                        c.setName("Soins");
                        c.setDescription("Soins du visage et du corps");
                        return categoryRepository.save(c);
                    });

            Care care = careRepository.findAll().stream()
                    .filter(c -> "Soin visage".equals(c.getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        Care c = new Care();
                        c.setName("Soin visage");
                        c.setDescription("Soin hydratant du visage — 45 min");
                        c.setPrice(5000);
                        c.setDuration(45);
                        c.setStatus(CareStatus.ACTIVE);
                        c.setCategory(category);
                        c.setDisplayOrder(1);
                        return careRepository.save(c);
                    });
            careId = care.getId();

            // Opening hours Mon→Sat 9h–19h (only create if not already set)
            if (openingHourRepository.count() == 0) {
                for (int day = 1; day <= 6; day++) {
                    OpeningHour oh = new OpeningHour();
                    oh.setDayOfWeek(day);
                    oh.setOpenTime(LocalTime.of(9, 0));
                    oh.setCloseTime(LocalTime.of(19, 0));
                    openingHourRepository.save(oh);
                }
            }
        } finally {
            TenantContext.clear();
        }

        String proToken = tokenService.generateToken(pro.getId(), pro.getEmail(), pro.getRole().name());
        String clientToken = tokenService.generateToken(client.getId(), client.getEmail(), client.getRole().name());

        LocalDate targetDate = nextWeekday(LocalDate.now().plusDays(2));

        Map<String, Object> body = new HashMap<>();
        body.put("salonSlug", SALON_SLUG);
        body.put("salonName", SALON_NAME);
        body.put("careId", careId);
        body.put("proUserId", pro.getId());
        body.put("proEmail", pro.getEmail());
        body.put("proToken", proToken);
        body.put("clientUserId", client.getId());
        body.put("clientEmail", client.getEmail());
        body.put("clientName", client.getName());
        body.put("clientToken", clientToken);
        body.put("suggestedDate", targetDate.toString());
        body.put("suggestedTime", "10:00");

        logger.info("[smoke] seed complete — proId={} clientId={} careId={}",
                pro.getId(), client.getId(), careId);
        return ResponseEntity.ok(body);
    }

    /**
     * Hard reset for between-test isolation: wipes bookings + mirrors from the
     * seeded tenant schema and the shared client history. Keeps the tenant,
     * users and care catalog so the next seed call is instant.
     */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        logger.info("[smoke] /api/test/reset invoked");

        try {
            clientBookingHistoryRepository.deleteAll();
        } catch (Exception e) {
            logger.warn("[smoke] client history wipe skipped: {}", e.getMessage());
        }

        if (tenantRepository.findBySlug(SALON_SLUG).isPresent()) {
            TenantContext.setCurrentTenant(SALON_SLUG);
            try {
                careBookingRepository.deleteAll();
            } catch (Exception e) {
                logger.warn("[smoke] tenant bookings wipe skipped: {}", e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }

        return ResponseEntity.noContent().build();
    }

    private static LocalDate nextWeekday(LocalDate from) {
        LocalDate d = from;
        while (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    // Reference the enum so we don't get an "unused import" warning while
    // preserving a useful symbol for future cancel/active filters.
    @SuppressWarnings("unused")
    private static final CareBookingStatus ACTIVE_MARKER = CareBookingStatus.PENDING;
}
