package com.luxpretty.app.users.domain;

public enum Role {
    PRO,
    EMPLOYEE,
    COMMERCIAL,
    ADMIN,
    /** @deprecated remove in Task 9 once all callers migrated. */
    @Deprecated USER;

    public ScopeType expectedScopeType() {
        return switch (this) {
            case PRO, EMPLOYEE -> ScopeType.TENANT;
            case COMMERCIAL, ADMIN -> ScopeType.GLOBAL;
            case USER -> throw new IllegalStateException("USER is deprecated; absence of assignment = CLIENT implicite");
        };
    }
}
