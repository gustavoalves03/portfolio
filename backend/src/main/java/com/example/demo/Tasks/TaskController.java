package com.example.demo.Tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin // utile si pas de proxy Angular
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service = service; }

    @GetMapping
    public Page<TaskDto> list(Pageable pageable) { return service.list(pageable); }

    @PostMapping
    public TaskDto create(@Valid @RequestBody TaskDto dto) { return service.create(dto); }

    @GetMapping("/{id}")
    public TaskDto get(@PathVariable Long id) { return service.get(id); }

    @PutMapping("/{id}")
    public TaskDto update(@PathVariable Long id, @Valid @RequestBody TaskDto dto) { return service.update(id, dto); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }
}
