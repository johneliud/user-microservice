package io.github.johneliud.user_microservice.dto;

public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AccessTokenResponse of(String accessToken, long expiresIn) {
        return new AccessTokenResponse(accessToken, "Bearer", expiresIn);
    }
}