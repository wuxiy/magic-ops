package top.cywu.magicops.console.dto.security;

import top.cywu.magicops.console.entity.security.UserEntity;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        String email,
        boolean enabled,
        boolean locked,
        Instant createdAt
) {
    public static UserResponse from(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.isEnabled(),
                entity.isLocked(),
                entity.getCreatedAt()
        );
    }
}
