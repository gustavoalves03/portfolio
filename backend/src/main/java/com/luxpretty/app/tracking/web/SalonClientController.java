package com.luxpretty.app.tracking.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.tracking.app.SalonClientService;
import com.luxpretty.app.tracking.web.dto.CreateSalonClientRequest;
import com.luxpretty.app.tracking.web.dto.SalonClientResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pro/clients")
public class SalonClientController {

    private final SalonClientService salonClientService;

    public SalonClientController(SalonClientService salonClientService) {
        this.salonClientService = salonClientService;
    }

    @GetMapping("/search")
    public List<SalonClientResponse> search(@RequestParam String q) {
        return salonClientService.search(q);
    }

    @GetMapping("/recent")
    public List<SalonClientResponse> recent() {
        return salonClientService.recent();
    }

    @GetMapping("/{id}")
    public SalonClientResponse getById(@PathVariable Long id) {
        return salonClientService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalonClientResponse create(
            @Valid @RequestBody CreateSalonClientRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return salonClientService.create(request, principal.getId());
    }

    @PostMapping("/{salonClientId}/link/{userId}")
    public SalonClientResponse link(
            @PathVariable Long salonClientId,
            @PathVariable Long userId) {
        return salonClientService.linkToUser(salonClientId, userId);
    }
}
