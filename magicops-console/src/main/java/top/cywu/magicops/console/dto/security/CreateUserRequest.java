package top.cywu.magicops.console.dto.security;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        String displayName,
        String email
) {}
