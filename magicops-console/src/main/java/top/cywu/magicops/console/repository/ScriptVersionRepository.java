package top.cywu.magicops.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.ScriptVersionEntity;

import java.util.List;

public interface ScriptVersionRepository extends JpaRepository<ScriptVersionEntity, Long> {

    List<ScriptVersionEntity> findByScriptIdOrderByCreatedAtDesc(Long scriptId);
}
