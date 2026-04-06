package com.prettyface.app.availability.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.availability.app.HolidayService;
import com.prettyface.app.availability.domain.HolidayException;
import com.prettyface.app.availability.repo.HolidayExceptionRepository;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pro/holidays")
public class HolidayController {

    private final HolidayService holidayService;
    private final HolidayExceptionRepository exceptionRepo;
    private final TenantRepository tenantRepository;

    public HolidayController(HolidayService holidayService,
                              HolidayExceptionRepository exceptionRepo,
                              TenantRepository tenantRepository) {
        this.holidayService = holidayService;
        this.exceptionRepo = exceptionRepo;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/upcoming")
    public List<HolidayService.HolidayInfo> getUpcomingHolidays(
            @AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        return holidayService.getUpcomingHolidays(tenant.getAddressCountry(), 12);
    }

    @PutMapping("/exceptions/{date}")
    public Map<String, Object> toggleException(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable LocalDate date,
            @RequestBody Map<String, Boolean> body) {
        Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        // Set tenant context so that the exception is saved in the correct schema
        TenantContext.setCurrentTenant(tenant.getSlug());
        try {
            boolean open = Boolean.TRUE.equals(body.get("open"));

            HolidayException exception = exceptionRepo.findByHolidayDate(date)
                    .orElseGet(() -> {
                        HolidayException ex = new HolidayException();
                        ex.setHolidayDate(date);
                        return ex;
                    });
            exception.setOpen(open);
            exceptionRepo.save(exception);

            return Map.of("date", date, "open", open);
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/exceptions")
    public List<Map<String, Object>> listExceptions(
            @AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        TenantContext.setCurrentTenant(tenant.getSlug());
        try {
            return exceptionRepo.findAll().stream()
                    .map(ex -> Map.<String, Object>of(
                            "date", ex.getHolidayDate(),
                            "open", ex.isOpen()
                    ))
                    .toList();
        } finally {
            TenantContext.clear();
        }
    }
}
