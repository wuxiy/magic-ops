package top.cywu.magicops.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.ScriptDraftEntity;

import java.util.Optional;

public interface ScriptDraftRepository extends JpaRepository<ScriptDraftEntity, Long> {

    Optional<ScriptDraftEntity> findByScriptId(Long scriptId);
}
