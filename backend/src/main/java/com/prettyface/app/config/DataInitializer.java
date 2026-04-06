package com.prettyface.app.config;

import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.multitenancy.TenantSchemaManager;
import com.prettyface.app.post.domain.Post;
import com.prettyface.app.post.domain.PostType;
import com.prettyface.app.post.repo.PostRepository;
import com.prettyface.app.tenant.app.SlugUtils;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    @ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
    CommandLineRunner initDatabase(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TenantRepository tenantRepository,
            TenantSchemaManager tenantSchemaManager,
            CategoryRepository categoryRepository,
            CareRepository careRepository,
            OpeningHourRepository openingHourRepository,
            PostRepository postRepository
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
            Tenant salonSophie = createTenant(tenantRepository, sophie);
            salonSophie.setName("L'Atelier de Sophie");
            salonSophie.setDescription("Institut de beauté spécialisé dans les soins du visage et le bien-être. " +
                    "Venez découvrir nos soins personnalisés dans un cadre chaleureux.");
            salonSophie.setCategoryNames("Soins visage,Soins corps,Épilation");
            salonSophie.setCategorySlugs("soins-visage,soins-corps,epilation");
            salonSophie.setAddressStreet("25 Rue du Faubourg Saint-Antoine");
            salonSophie.setAddressPostalCode("75011");
            salonSophie.setAddressCity("Paris");
            salonSophie.setAddressCountry("FR");
            salonSophie.setPhone("+33 1 43 72 15 80");
            salonSophie.setContactEmail("sophie@prettyface.com");
            tenantRepository.save(salonSophie);
            tenantSchemaManager.provisionSchema(salonSophie.getSlug());
            seedSalonSophie(salonSophie.getSlug(), categoryRepository, careRepository, openingHourRepository);
            logger.info("✅ Salon '{}' créé (slug: {}, pro: {})", salonSophie.getName(), salonSophie.getSlug(), sophie.getEmail());

            // ── Pro 2: Camille's salon ──
            User camille = createUser(userRepository, passwordEncoder,
                    "Camille Dubois", "camille@prettyface.com", DEFAULT_PASSWORD, Role.PRO);
            Tenant salonCamille = createTenant(tenantRepository, camille);
            salonCamille.setName("Beauté by Camille");
            salonCamille.setDescription("Espace dédié à la beauté des ongles et au maquillage professionnel. " +
                    "Nail art, pose de vernis semi-permanent et maquillage événementiel.");
            salonCamille.setCategoryNames("Ongles,Maquillage");
            salonCamille.setCategorySlugs("ongles,maquillage");
            salonCamille.setAddressStreet("12 Rue de la République");
            salonCamille.setAddressPostalCode("69003");
            salonCamille.setAddressCity("Lyon");
            salonCamille.setAddressCountry("FR");
            salonCamille.setPhone("+33 4 78 60 22 45");
            salonCamille.setContactEmail("camille@prettyface.com");
            tenantRepository.save(salonCamille);
            tenantSchemaManager.provisionSchema(salonCamille.getSlug());
            seedSalonCamille(salonCamille.getSlug(), categoryRepository, careRepository, openingHourRepository);
            logger.info("✅ Salon '{}' créé (slug: {}, pro: {})", salonCamille.getName(), salonCamille.getSlug(), camille.getEmail());

            // ── Pro 3: Isabelle's salon (Bordeaux) ──
            User isabelle = createUser(userRepository, passwordEncoder,
                    "Isabelle Dupont", "isabelle@prettyface.com", DEFAULT_PASSWORD, Role.PRO);
            Tenant salonIsabelle = createTenant(tenantRepository, isabelle);
            salonIsabelle.setName("Éclat Naturel");
            salonIsabelle.setDescription("Soins bio et naturels pour le visage et le corps. " +
                    "Produits certifiés bio, ambiance zen et cocooning.");
            salonIsabelle.setCategoryNames("Soins visage,Soins corps");
            salonIsabelle.setCategorySlugs("soins-visage,soins-corps");
            salonIsabelle.setAddressStreet("8 Cours de l'Intendance");
            salonIsabelle.setAddressPostalCode("33000");
            salonIsabelle.setAddressCity("Bordeaux");
            salonIsabelle.setAddressCountry("FR");
            salonIsabelle.setPhone("+33 5 56 48 30 12");
            salonIsabelle.setContactEmail("isabelle@prettyface.com");
            tenantRepository.save(salonIsabelle);
            tenantSchemaManager.provisionSchema(salonIsabelle.getSlug());
            seedSalonIsabelle(salonIsabelle.getSlug(), categoryRepository, careRepository, openingHourRepository);
            logger.info("✅ Salon '{}' créé (slug: {}, pro: {})", salonIsabelle.getName(), salonIsabelle.getSlug(), isabelle.getEmail());

            // ── Clients ──
            createUser(userRepository, passwordEncoder,
                    "Marie Leroy", "marie@test.com", DEFAULT_PASSWORD, Role.USER);
            createUser(userRepository, passwordEncoder,
                    "Julie Petit", "julie@test.com", DEFAULT_PASSWORD, Role.USER);
            createUser(userRepository, passwordEncoder,
                    "Clara Moreau", "clara@test.com", DEFAULT_PASSWORD, Role.USER);

            // ── Posts (images from Unsplash) ──
            seedPosts(postRepository);
            logger.info("✅ Posts de démonstration créés");

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

    private Tenant createTenant(TenantRepository tenantRepository, User owner) {
        String baseSlug = SlugUtils.toSlug(owner.getName());
        String slug = baseSlug;
        int counter = 2;

        while (tenantRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        Tenant tenant = Tenant.builder()
                .slug(slug)
                .name(owner.getName())
                .ownerId(owner.getId())
                .status(TenantStatus.ACTIVE)
                .build();

        Tenant saved = tenantRepository.save(tenant);
        logger.info("  → Demo tenant created: {} ({})", saved.getName(), saved.getSlug());
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

    // ── Isabelle's salon: soins bio ──
    private void seedSalonIsabelle(String slug,
                                    CategoryRepository categoryRepo,
                                    CareRepository careRepo,
                                    OpeningHourRepository openingHourRepo) {
        TenantContext.setCurrentTenant(slug);
        try {
            Category visage = saveCategory(categoryRepo, "Soins visage", "Soins naturels et bio pour le visage");
            Category corps = saveCategory(categoryRepo, "Soins corps", "Soins corporels aux produits bio");

            saveCare(careRepo, "Soin éclat bio", "Soin illuminateur aux huiles essentielles bio",
                    6500, 60, visage, 1);
            saveCare(careRepo, "Facial detox", "Nettoyage profond aux argiles naturelles",
                    5000, 50, visage, 2);
            saveCare(careRepo, "Massage aux pierres chaudes", "Massage relaxant aux pierres de basalte",
                    7500, 75, corps, 1);
            saveCare(careRepo, "Enveloppement algues", "Soin minceur et détoxifiant aux algues marines",
                    6000, 60, corps, 2);

            // Opening hours: Tue-Sat 9h30-18h30
            for (int day = 2; day <= 6; day++) {
                saveOpeningHour(openingHourRepo, day, LocalTime.of(9, 30), LocalTime.of(18, 30));
            }
        } finally {
            TenantContext.clear();
        }
    }

    // ── Demo posts with image URLs ──
    private void seedPosts(PostRepository postRepo) {
        // Post images: use downloadable public URLs as paths
        // In production these would be uploaded files; for demo we store URLs directly
        createPost(postRepo, PostType.PHOTO, "Résultat soin éclat",
                null,
                "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?w=600",
                null, "Soin éclat bio");
        createPost(postRepo, PostType.PHOTO, "Manucure du jour",
                null,
                "https://images.unsplash.com/photo-1604654894610-df63bc536371?w=600",
                null, "Manucure classique");
        createPost(postRepo, PostType.PHOTO, "Massage relaxant",
                null,
                "https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=600",
                null, "Massage relaxant");
        createPost(postRepo, PostType.BEFORE_AFTER, "Transformation soin anti-âge",
                "https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?w=600",
                "https://images.unsplash.com/photo-1595476108010-b4d1f102b1b1?w=600",
                null, "Soin anti-âge premium");
        createPost(postRepo, PostType.PHOTO, "Ambiance zen du salon",
                null,
                "https://images.unsplash.com/photo-1540555700478-4be289fbec6f?w=600",
                null, null);
        createPost(postRepo, PostType.PHOTO, "Nail art créatif",
                null,
                "https://images.unsplash.com/photo-1607779097040-26e80aa78e66?w=600",
                null, "Nail art créatif");
    }

    private void createPost(PostRepository repo, PostType type, String caption,
                            String beforePath, String afterPath, Long careId, String careName) {
        Post post = new Post();
        post.setType(type);
        post.setCaption(caption);
        post.setBeforeImagePath(beforePath);
        post.setAfterImagePath(afterPath);
        post.setCareId(careId);
        post.setCareName(careName);
        repo.save(post);
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
