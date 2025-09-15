package com.fleurdecoquillage.app.users.web;

import com.fleurdecoquillage.app.users.app.UserService;
import com.fleurdecoquillage.app.users.web.dto.UserRequest;
import com.fleurdecoquillage.app.users.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @GetMapping
    public Page<UserResponse> list(Pageable pageable) { return service.list(pageable); }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public UserResponse create(@RequestBody @Valid UserRequest req) { return service.create(req); }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody @Valid UserRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }
}

