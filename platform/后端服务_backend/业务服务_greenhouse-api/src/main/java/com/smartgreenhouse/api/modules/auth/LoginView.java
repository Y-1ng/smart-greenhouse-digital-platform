package com.smartgreenhouse.api.modules.auth;

public record LoginView(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserView user
) {

    public record UserView(
            Long userId,
            String username,
            String displayName,
            String roleCode
    ) {
    }
}
