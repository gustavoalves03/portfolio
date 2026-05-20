package com.luxpretty.app.bookings.repo;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.category.domain.Category;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class CareBookingRepositoryTests {

    @Autowired CareBookingRepository repo;
    @Autowired UserRepository userRepo;
    @Autowired CareRepository careRepo;
    @Autowired CategoryRepository categoryRepo;

    private User persistMinimalUser() {
        return userRepo.save(User.builder()
                .email("test-booking-user@test.com")
                .name("Test User")
                .provider(AuthProvider.LOCAL)
                .build());
    }

    private Care persistMinimalCare() {
        Category cat = new Category();
        cat.setName("Test Category");
        cat = categoryRepo.save(cat);

        Care care = new Care();
        care.setName("Test Care");
        care.setPrice(50);
        care.setDescription("desc");
        care.setStatus(CareStatus.ACTIVE);
        care.setDuration(30);
        care.setCategory(cat);
        return careRepo.save(care);
    }

    @Test
    void persistsCancellationReason() {
        User u = persistMinimalUser();
        Care c = persistMinimalCare();
        CareBooking b = new CareBooking();
        b.setUser(u);
        b.setCare(c);
        b.setStatus(CareBookingStatus.CANCELLED);
        b.setCancellationReason("LEGACY_NO_EMPLOYEE");
        b.setAppointmentDate(LocalDate.of(2026, 6, 1));
        b.setAppointmentTime(LocalTime.of(10, 0));
        b.setQuantity(1);
        CareBooking saved = repo.saveAndFlush(b);
        CareBooking reloaded = repo.findById(saved.getId()).orElseThrow();
        assertEquals("LEGACY_NO_EMPLOYEE", reloaded.getCancellationReason());
    }
}
