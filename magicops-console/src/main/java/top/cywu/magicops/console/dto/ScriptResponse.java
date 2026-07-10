package top.cywu.magicops.console.dto;

import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.core.model.ScriptStatus;
import top.cywu.magicops.core.model.ScriptType;

import java.time.Instant;

/**
 * 脚本响应 DTO。
 */
public record ScriptResponse(
        Long id,
        String name,
        String projectCode,
        ScriptType scriptType,
        ScriptStatus status,
        Long currentVersionId,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static ScriptResponse from(ScriptEntity entity) {
        return new ScriptResponse(
                entity.getId(),
                entity.getName(),
                entity.getProjectCode(),
                entity.getScriptType(),
                entity.getStatus(),
                entity.getCurrentVersionId(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
