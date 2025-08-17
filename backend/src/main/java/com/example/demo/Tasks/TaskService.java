
package com.example.demo.Tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final TaskRepository repo;
    public TaskService(TaskRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public Page<TaskDto> list(Pageable pageable) {
        return repo.findAll(pageable).map(TaskMapper::toDto);
    }

    @Transactional
    public TaskDto create(TaskDto dto) {
        Task saved = repo.save(TaskMapper.toEntity(dto));
        return TaskMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public TaskDto get(Long id) {
        return repo.findById(id).map(TaskMapper::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
    }

    @Transactional
    public TaskDto update(Long id, TaskDto dto) {
        Task t = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
        TaskMapper.updateEntity(t, dto);
        return TaskMapper.toDto(repo.save(t));
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
