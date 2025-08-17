package com.example.demo.Tasks;

public class TaskMapper {
    public static TaskDto toDto(Task t) {
        return new TaskDto(t.getId(), t.getTitle());
    }
    public static Task toEntity(TaskDto dto) {
        Task t = new Task();
        t.setTitle(dto.title());
        return t;
    }
    public static void updateEntity(Task t, TaskDto dto) {
        if (dto.title() != null) t.setTitle(dto.title());
    }
}
