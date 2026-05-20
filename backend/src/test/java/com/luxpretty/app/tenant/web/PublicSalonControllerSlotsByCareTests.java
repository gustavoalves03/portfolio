package com.luxpretty.app.tenant.web;

import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.availability.app.AvailabilityService;
import com.luxpretty.app.availability.app.BlockedSlotService;
import com.luxpretty.app.availability.app.HolidayAvailabilityService;
import com.luxpretty.app.availability.app.SlotAvailabilityService;
import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.app.ClientBookingHistoryService;
import com.luxpretty.app.bookings.web.dto.EmployeeSlotState;
import com.luxpretty.app.bookings.web.dto.SlotWithEmployees;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.common.error.GlobalExceptionHandler;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.config.CsrfLoggingFilter;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.post.app.PostService;
import com.luxpretty.app.tenant.app.SalonPreviewTokenService;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the public endpoint GET /api/salon/{slug}/slots/by-care
 * that returns per-employee slot availability for the client booking flow.
 */
@WebMvcTest(PublicSalonController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PublicSalonControllerSlotsByCareTests {

    @Autowired private MockMvc mvc;

    @MockBean private TenantService tenantService;
    @MockBean private CategoryRepository categoryRepository;
    @MockBean private AvailabilityService availabilityService;
    @MockBean private BlockedSlotService blockedSlotService;
    @MockBean private SlotAvailabilityService slotAvailabilityService;
    @MockBean private HolidayAvailabilityService holidayAvailabilityService;
    @MockBean private com.luxpretty.app.availability.app.ClosedDaysService closedDaysService;
    @MockBean private CareBookingService careBookingService;
    @MockBean private UserRepository userRepository;
    @MockBean private ClientBookingHistoryService clientBookingHistoryService;
    @MockBean private EmployeeService employeeService;
    @MockBean private PostService postService;
    @MockBean private SalonPreviewTokenService previewTokenService;

    @MockBean private TokenService tokenService;
    @MockBean private UserRoleService userRoleService;
    @MockBean private com.luxpretty.app.tenant.repo.TenantRepository tenantRepository;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private CustomOidcUserService customOidcUserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @SpyBean private RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean private RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean private TenantFilter tenantFilter;
    @SpyBean private CsrfLoggingFilter csrfLoggingFilter;

    @Test
    void availableSlotsByCare_returnsFanOutPayload() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setSlug("test-salon");
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantService.findBySlug("test-salon")).thenReturn(Optional.of(tenant));
        when(slotAvailabilityService.getAvailableSlotsForCareWithEmployees(LocalDate.of(2026, 6, 1), 7L))
                .thenReturn(List.of(new SlotWithEmployees("10:00", List.of(
                        EmployeeSlotState.available(1L, "Marie"),
                        EmployeeSlotState.busy(2L, "Sophie")
                ))));
        mvc.perform(get("/api/salon/test-salon/slots/by-care")
                        .param("date", "2026-06-01")
                        .param("careId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].time").value("10:00"))
                .andExpect(jsonPath("$[0].employees[0].name").value("Marie"))
                .andExpect(jsonPath("$[0].employees[0].available").value(true))
                .andExpect(jsonPath("$[0].employees[1].available").value(false))
                .andExpect(jsonPath("$[0].employees[1].reason").value("BUSY"));
    }

    @Test
    void availableSlotsByCare_unknownSlug_returns404() throws Exception {
        when(tenantService.findBySlug("nope")).thenReturn(Optional.empty());
        mvc.perform(get("/api/salon/nope/slots/by-care").param("date", "2026-06-01").param("careId", "7"))
                .andExpect(status().isNotFound());
    }

    @Test
    void availableSlotsByCare_inactiveTenant_returns404() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setSlug("inactive");
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantService.findBySlug("inactive")).thenReturn(Optional.of(tenant));
        mvc.perform(get("/api/salon/inactive/slots/by-care").param("date", "2026-06-01").param("careId", "7"))
                .andExpect(status().isNotFound());
    }
}
