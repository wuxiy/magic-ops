package top.cywu.magicops.console.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.ApprovalEntity;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepository extends JpaRepository<ApprovalEntity, Long> {

    Optional<ApprovalEntity> findByScriptVersionId(Long scriptVersionId);

    List<ApprovalEntity> findBySubmittedBy(String submittedBy);

    Page<ApprovalEntity> findByDecision(String decision, Pageable pageable);
}
