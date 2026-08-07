package top.cywu.magicops.console.api.security;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.console.dto.security.AssignRoleRequest;
import top.cywu.magicops.console.dto.security.CreateUserRequest;
import top.cywu.magicops.console.dto.security.UserResponse;
import top.cywu.magicops.console.entity.security.RoleEntity;
import top.cywu.magicops.console.entity.security.UserEntity;
import top.cywu.magicops.console.security.CurrentActor;
import top.cywu.magicops.console.service.security.UserService;
import top.cywu.magicops.core.model.Permissions;

import java.util.List;
import java.util.Map;

/**
 * 用户管理 REST API。
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserEntity user = userService.createUser(
                request.username(), request.password(),
                request.displayName(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        List<UserResponse> users = userService.findAll().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> get(@PathVariable Long id) {
        // UserService doesn't have findById, use findAll and filter
        UserEntity user = userService.findAll().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<Map<String, Object>> assignRole(@PathVariable Long id,
                                                          @Valid @RequestBody AssignRoleRequest request) {
        // 切片 31：授予人强制取登录身份，忽略客户端传值
        userService.assignRole(id, request.roleName(), CurrentActor.username());
        List<String> roles = userService.getUserRoleNames(id);
        return ResponseEntity.ok(Map.of("userId", id, "roles", roles));
    }

    /**
     * 回收用户角色（切片 31）。需要 user:manage 权限，关键审计。
     */
    @DeleteMapping("/{id}/roles/{roleId}")
    public ResponseEntity<Map<String, Object>> revokeRole(@PathVariable Long id,
                                                          @PathVariable Long roleId) {
        userService.revokeRole(id, roleId, CurrentActor.username());
        List<String> roles = userService.getUserRoleNames(id);
        return ResponseEntity.ok(Map.of("userId", id, "roles", roles));
    }

    @GetMapping("/{id}/roles")
    public ResponseEntity<List<RoleEntity>> getRoles(@PathVariable Long id) {
        List<RoleEntity> roles = userService.getUserRoles(id);
        return ResponseEntity.ok(roles);
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<Void> setEnabled(@PathVariable Long id,
                                           @RequestBody Map<String, Boolean> request) {
        boolean enabled = request.getOrDefault("enabled", true);
        userService.setEnabled(id, enabled);
        return ResponseEntity.ok().build();
    }
}
