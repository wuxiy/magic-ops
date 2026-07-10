package top.cywu.magicops.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.core.model.ScriptStatus;

import java.util.List;
import java.util.Optional;

public interface ScriptRepository extends JpaRepository<ScriptEntity, Long> {

    Optional<ScriptEntity> findByName(String name);

    List<ScriptEntity> findByStatus(ScriptStatus status);

    List<ScriptEntity> findByProjectCode(String projectCode);
}
