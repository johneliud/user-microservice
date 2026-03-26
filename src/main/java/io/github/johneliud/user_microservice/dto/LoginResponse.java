package io.github.johneliud.user_microservice.dto;

public sealed interface LoginResponse permits AuthResponse, MfaRequiredResponse {}
