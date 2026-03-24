package com.prettyface.app.auth;

import com.prettyface.app.users.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private Long id;
    private String email;
    private String name;
    private Map<String, Object> attributes;

    public static UserPrincipal create(User user) {
        return new UserPrincipal(
            user.getId(),
            user.getEmail(),
            user.getName(),
            null
        );
    }

    public static UserPrincipal create(User user, Map<String, Object> attributes) {
        UserPrincipal userPrincipal = create(user);
        return new UserPrincipal(
            userPrincipal.getId(),
            userPrincipal.getEmail(),
            userPrincipal.getName(),
            attributes
        );
    }
}
