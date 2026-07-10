package top.cywu.magicops.console.dto.security;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(
        @NotBlank String roleName,
        String grantedBy
) {}
