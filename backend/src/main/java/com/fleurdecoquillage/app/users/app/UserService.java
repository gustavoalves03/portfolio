package com.fleurdecoquillage.app.users.app;

import com.fleurdecoquillage.app.users.domain.User;
import com.fleurdecoquillage.app.users.repo.UserRepository;
import com.fleurdecoquillage.app.users.web.dto.UserRequest;
import com.fleurdecoquillage.app.users.web.dto.UserResponse;
import com.fleurdecoquillage.app.users.web.mapper.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository repo;
    public UserService(UserRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return repo.findAll(pageable).map(UserMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return repo.findById(id).map(UserMapper::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional
    public UserResponse create(UserRequest req) {
        User saved = repo.save(UserMapper.toEntity(req));
        return UserMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest req) {
        User u = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        UserMapper.updateEntity(u, req);
        return UserMapper.toResponse(repo.save(u));
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }
}

