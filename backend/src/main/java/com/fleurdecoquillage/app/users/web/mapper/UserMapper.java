package com.fleurdecoquillage.app.users.web.mapper;

import com.fleurdecoquillage.app.users.domain.User;
import com.fleurdecoquillage.app.users.web.dto.UserRequest;
import com.fleurdecoquillage.app.users.web.dto.UserResponse;

public class UserMapper {
    public static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail());
    }
    public static User toEntity(UserRequest req) {
        User u = new User();
        updateEntity(u, req);
        return u;
    }
    public static void updateEntity(User u, UserRequest req) {
        u.setName(req.name());
        u.setEmail(req.email());
    }
}

