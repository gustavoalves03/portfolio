package com.luxpretty.app.users.domain;

public enum Role {
    PRO,
    EMPLOYEE,
    COMMERCIAL,
    ADMIN;

    public ScopeType expectedScopeType() {
        return switch (this) {
            case PRO, EMPLOYEE -> ScopeType.TENANT;
            case COMMERCIAL, ADMIN -> ScopeType.GLOBAL;
        };
    }
}
