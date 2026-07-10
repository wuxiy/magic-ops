package top.cywu.magicops.console.api.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.console.entity.security.PermissionEntity;
import top.cywu.magicops.console.entity.security.RoleEntity;
import top.cywu.magicops.console.repository.security.PermissionRepository;
import top.cywu.magicops.console.repository.security.RoleRepository;

import java.util.List;

/**
 * 角色和权限查询 REST API。
 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleController(RoleRepository roleRepository,
                          PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    public ResponseEntity<List<RoleEntity>> listRoles() {
        return ResponseEntity.ok(roleRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleEntity> getRole(@PathVariable Long id) {
        RoleEntity role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));
        return ResponseEntity.ok(role);
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionEntity>> listPermissions() {
        return ResponseEntity.ok(permissionRepository.findAll());
    }

    @GetMapping("/permissions/{category}")
    public ResponseEntity<List<PermissionEntity>> listPermissionsByCategory(
            @PathVariable String category) {
        return ResponseEntity.ok(permissionRepository.findByCategory(category));
    }
}
