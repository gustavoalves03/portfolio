package com.fleurdecoquillage.app.availability.web;

import com.fleurdecoquillage.app.availability.app.BlockedSlotService;
import com.fleurdecoquillage.app.availability.web.dto.BlockedSlotRequest;
import com.fleurdecoquillage.app.availability.web.dto.BlockedSlotResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pro/blocked-slots")
public class BlockedSlotController {

    private final BlockedSlotService service;

    public BlockedSlotController(BlockedSlotService service) {
        this.service = service;
    }

    @GetMapping
    public List<BlockedSlotResponse> list() {
        return service.listFuture();
    }

    @PostMapping
    public BlockedSlotResponse create(@RequestBody @Valid BlockedSlotRequest req) {
        return service.create(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
