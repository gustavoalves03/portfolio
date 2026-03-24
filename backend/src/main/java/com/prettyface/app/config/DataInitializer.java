package com.prettyface.app.config;

import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tenant.app.TenantProvisioningService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalTime;

@Configuration
@Profile("!test")
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_PASSWORD = "Password1!";

    @Bean
    CommandLineRunner initDatabase(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TenantProvisioningService provisioningService,
            TenantRepository tenantRepository,
            CategoryRepository categoryRepository,
            CareRepository careRepository,
            OpeningHourRepository openingHourRepository
    ) {
        return args -> {
            if (userRepository.count() > 0) {
                logger.info("Database already seeded — skipping.");
                return;
            }

            logger.info("=== Seeding dev data ===");

            // ── Admin ──
            User admin = createUser(userRepository, passwordEncoder,
                    "Pretty Face Admin", "admin@prettyface.com", "Admin2026!", Role.ADMIN);

            // ── Pro 1: Sophie's salon ──
            User sophie = createUser(userRepository, passwordEncoder,
                    "Sophie Martin", "sophie@prettyface.com", DEFAULT_PASSWORD, Role.PRO);
            Tenant salonSophie = provisioningService.provision(sophie);
            salonSophie.setName("L'Atelier de Sophie");
            salonSophie.setDescription("Institut de beauté spécialisé dans les soins du visage et le bien-être. " +
                    "Venez découvrir nos soins personnalisés dans un cadre chaleureux.");
            salonSophie.setCategoryNames("Soins visage,Soins corps,Épilation");
            salonSophie.setCategorySlugs("soins-visage,soins-corps,epilation");
            tenantRepository.save(salonSophie);
            seedSalonSophie(salonSophie.getSlug(), categoryRepository, careRepository, openingHourRepository);
            logger.info("✅ Salon '{}' créé (slug: {}, pro: {})", salonSophie.getName(), salonSophie.getSlug(), sophie.getEmail());

            // ── Pro 2: Camille's salon ──
            User camille = createUser(userRepository, passwordEncoder,
                    "Camille Dubois", "camille@prettyface.com", DEFAULT_PASSWORD, Role.PRO);
            Tenant salonCamille = provisioningService.provision(camille);
            salonCamille.setName("Beauté by Camille");
            salonCamille.setDescription("Espace dédié à la beauté des ongles et au maquillage professionnel. " +
                    "Nail art, pose de vernis semi-permanent et maquillage événementiel.");
            salonCamille.setCategoryNames("Ongles,Maquillage");
            salonCamille.setCategorySlugs("ongles,maquillage");
            tenantRepository.save(salonCamille);
            seedSalonCamille(salonCamille.getSlug(), categoryRepository, careRepository, openingHourRepository);
            logger.info("✅ Salon '{}' créé (slug: {}, pro: {})", salonCamille.getName(), salonCamille.getSlug(), camille.getEmail());

            // ── Clients ──
            User client1 = createUser(userRepository, passwordEncoder,
                    "Marie Leroy", "marie@test.com", DEFAULT_PASSWORD, Role.USER);
            User client2 = createUser(userRepository, passwordEncoder,
                    "Julie Petit", "julie@test.com", DEFAULT_PASSWORD, Role.USER);
            User client3 = createUser(userRepository, passwordEncoder,
                    "Clara Moreau", "clara@test.com", DEFAULT_PASSWORD, Role.USER);

            logger.info("=== Seeding complete ===");
            logger.info("Comptes pro:    sophie@prettyface.com / {}  |  camille@prettyface.com / {}", DEFAULT_PASSWORD, DEFAULT_PASSWORD);
            logger.info("Comptes client: marie@test.com / {}  |  julie@test.com / {}  |  clara@test.com / {}", DEFAULT_PASSWORD, DEFAULT_PASSWORD, DEFAULT_PASSWORD);
            logger.info("Admin:          admin@prettyface.com / Admin2026!");
        };
    }

    private User createUser(UserRepository repo, PasswordEncoder encoder,
                            String name, String email, String password, Role role) {
        User user = User.builder()
                .name(name)
                .email(email)
                .password(encoder.encode(password))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .role(role)
                .build();
        User saved = repo.save(user);
        logger.info("  → User created: {} ({}) [{}]", name, email, role);
        return saved;
    }

    // ── Sophie's salon: soins visage, corps, épilation ──
    private void seedSalonSophie(String slug,
                                  CategoryRepository categoryRepo,
                                  CareRepository careRepo,
                                  OpeningHourRepository openingHourRepo) {
        TenantContext.setCurrentTenant(slug);
        try {
            // Categories
            Category visage = saveCategory(categoryRepo, "Soins visage", "Soins et traitements pour le visage");
            Category corps = saveCategory(categoryRepo, "Soins corps", "Massages et soins corporels");
            Category epilation = saveCategory(categoryRepo, "Épilation", "Épilation cire et fil");

            // Cares — visage
            saveCare(careRepo, "Soin hydratant visage", "Soin profond hydratant pour peaux sèches et déshydratées",
                    5500, 60, visage, 1);
            saveCare(careRepo, "Nettoyage de peau", "Nettoyage en profondeur avec extraction et masque purifiant",
                    4500, 45, visage, 2);
            saveCare(careRepo, "Soin anti-âge premium", "Soin liftant et repulpant à l'acide hyaluronique",
                    8500, 75, visage, 3);

            // Cares — corps
            saveCare(careRepo, "Massage relaxant", "Massage du dos et des épaules aux huiles essentielles",
                    6000, 60, corps, 1);
            saveCare(careRepo, "Gommage corps", "Gommage intégral au sel marin et beurre de karité",
                    5000, 45, corps, 2);

            // Cares — épilation
            saveCare(careRepo, "Épilation jambes complètes", "Épilation à la cire tiède",
                    3500, 45, epilation, 1);
            saveCare(careRepo, "Épilation maillot", "Épilation maillot classique ou brésilien",
                    2500, 30, epilation, 2);

            // Opening hours: Mon-Fri 9h-12h + 14h-19h, Sat 9h-17h
            for (int day = 1; day <= 5; day++) {
                saveOpeningHour(openingHourRepo, day, LocalTime.of(9, 0), LocalTime.of(12, 0));
                saveOpeningHour(openingHourRepo, day, LocalTime.of(14, 0), LocalTime.of(19, 0));
            }
            saveOpeningHour(openingHourRepo, 6, LocalTime.of(9, 0), LocalTime.of(17, 0)); // Saturday
            // Sunday closed (no rows)

        } finally {
            TenantContext.clear();
        }
    }

    // ── Camille's salon: ongles, maquillage ──
    private void seedSalonCamille(String slug,
                                   CategoryRepository categoryRepo,
                                   CareRepository careRepo,
                                   OpeningHourRepository openingHourRepo) {
        TenantContext.setCurrentTenant(slug);
        try {
            // Categories
            Category ongles = saveCategory(categoryRepo, "Ongles", "Manucure, pédicure et nail art");
            Category maquillage = saveCategory(categoryRepo, "Maquillage", "Maquillage professionnel");

            // Cares — ongles
            saveCare(careRepo, "Manucure classique", "Limage, cuticules et vernis classique",
                    2500, 30, ongles, 1);
            saveCare(careRepo, "Pose semi-permanent", "Pose de vernis semi-permanent longue tenue (3 semaines)",
                    3800, 45, ongles, 2);
            saveCare(careRepo, "Nail art créatif", "Pose gel avec décorations personnalisées",
                    5500, 60, ongles, 3);
            saveCare(careRepo, "Pédicure complète", "Soin des pieds avec gommage et vernis",
                    4000, 50, ongles, 4);

            // Cares — maquillage
            saveCare(careRepo, "Maquillage jour", "Maquillage naturel pour le quotidien",
                    4000, 30, maquillage, 1);
            saveCare(careRepo, "Maquillage soirée", "Maquillage sophistiqué pour événements",
                    6000, 45, maquillage, 2);
            saveCare(careRepo, "Maquillage mariée", "Maquillage complet mariée avec essai inclus",
                    12000, 90, maquillage, 3);

            // Opening hours: Tue-Sat 10h-19h (closed Mon & Sun)
            for (int day = 2; day <= 6; day++) {
                saveOpeningHour(openingHourRepo, day, LocalTime.of(10, 0), LocalTime.of(19, 0));
            }

        } finally {
            TenantContext.clear();
        }
    }

    private Category saveCategory(CategoryRepository repo, String name, String description) {
        Category cat = new Category();
        cat.setName(name);
        cat.setDescription(description);
        return repo.save(cat);
    }

    private void saveCare(CareRepository repo, String name, String description,
                          int priceInCents, int durationMinutes, Category category, int displayOrder) {
        Care care = new Care();
        care.setName(name);
        care.setDescription(description);
        care.setPrice(priceInCents);
        care.setDuration(durationMinutes);
        care.setCategory(category);
        care.setStatus(CareStatus.ACTIVE);
        care.setDisplayOrder(displayOrder);
        repo.save(care);
    }

    private void saveOpeningHour(OpeningHourRepository repo, int dayOfWeek, LocalTime open, LocalTime close) {
        OpeningHour oh = new OpeningHour();
        oh.setDayOfWeek(dayOfWeek);
        oh.setOpenTime(open);
        oh.setCloseTime(close);
        repo.save(oh);
    }
}
