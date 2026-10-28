package com.example.patrol_be.permission;

import com.example.patrol_be.model.PatrolRole;
import com.example.patrol_be.model.PatrolRolePermission;
import com.example.patrol_be.repository.PatrolRolePermissionRepo;
import com.example.patrol_be.repository.PatrolRoleRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RULE QUYỀN – CHỈ 1 CHỖ DUY NHẤT (đọc từ SPatrol_Role / SPatrol_RolePermission mỗi lần gọi, không cache).
 * Quyền cuối = quyền USER (mọi người đều có) OR quyền từng role hợp lệ; có role IsSuper -> full mọi group.
 */
@Slf4j
@Service
public class PermissionService {

    /** Role cơ bản, luôn áp cho mọi người. */
    public static final String BASE_ROLE = "USER";

    private final PatrolRoleRepo roleRepo;
    private final PatrolRolePermissionRepo rolePermissionRepo;

    public PermissionService(PatrolRoleRepo roleRepo, PatrolRolePermissionRepo rolePermissionRepo) {
        this.roleRepo = roleRepo;
        this.rolePermissionRepo = rolePermissionRepo;
    }

    /**
     * @param roles       role hợp lệ (có trong SPatrol_Role) của user; rỗng -> [USER]
     * @param permissions đủ mọi group
     */
    public record Resolved(List<String> roles, Map<PatrolGroup, Set<PatrolAction>> permissions) {
    }

    public Map<PatrolGroup, Set<PatrolAction>> resolve(String rawRoleColumn) {
        return resolveWithRoles(rawRoleColumn).permissions();
    }

    public boolean can(String rawRoleColumn, PatrolGroup group, PatrolAction action) {
        return resolve(rawRoleColumn).getOrDefault(group, Set.of()).contains(action);
    }

    /**
     * Tách cột Role (phân cách dấu phẩy), trim, uppercase.
     * Role không có trong SPatrol_Role -> log.warn rồi bỏ qua.
     */
    public Resolved resolveWithRoles(String rawRoleColumn) {
        Set<String> requested = new LinkedHashSet<>();
        if (rawRoleColumn != null) {
            for (String r : rawRoleColumn.split(",")) {
                String n = normalizeRole(r);
                if (!n.isEmpty()) requested.add(n);
            }
        }

        Set<String> lookup = new LinkedHashSet<>(requested);
        lookup.add(BASE_ROLE);

        Map<String, PatrolRole> known = roleRepo.findByRoleCodeIn(lookup).stream()
                .collect(Collectors.toMap(r -> normalizeRole(r.getRoleCode()), Function.identity(), (a, b) -> a));

        List<String> validRoles = new ArrayList<>();
        for (String r : requested) {
            if (known.containsKey(r)) {
                validRoles.add(r);
            } else {
                log.warn("Unknown role '{}' (Role column='{}'), ignored", r, rawRoleColumn);
            }
        }

        // USER luôn được áp, kể cả khi không có dòng trong SPatrol_Role.
        Set<String> effective = new LinkedHashSet<>();
        effective.add(BASE_ROLE);
        effective.addAll(validRoles);

        boolean isSuper = effective.stream()
                .map(known::get)
                .anyMatch(r -> r != null && Boolean.TRUE.equals(r.getIsSuper()));

        Map<PatrolGroup, Set<PatrolAction>> result = new EnumMap<>(PatrolGroup.class);
        for (PatrolGroup g : PatrolGroup.values()) {
            result.put(g, isSuper ? EnumSet.allOf(PatrolAction.class) : EnumSet.noneOf(PatrolAction.class));
        }

        if (!isSuper) {
            for (PatrolRolePermission p : rolePermissionRepo.findByRoleCodeIn(effective)) {
                PatrolGroup group = toGroup(p.getGroupCode());
                if (group == null) {
                    log.warn("Unknown GroupCode '{}' (RoleCode='{}') in SPatrol_RolePermission, ignored",
                            p.getGroupCode(), p.getRoleCode());
                    continue;
                }

                Set<PatrolAction> actions = result.get(group);
                if (Boolean.TRUE.equals(p.getCanBefore())) actions.add(PatrolAction.BEFORE);
                if (Boolean.TRUE.equals(p.getCanAfter())) actions.add(PatrolAction.AFTER);
                if (Boolean.TRUE.equals(p.getCanRecheck())) actions.add(PatrolAction.RECHECK);
                if (Boolean.TRUE.equals(p.getCanSummary())) actions.add(PatrolAction.SUMMARY);
            }
        }

        result.replaceAll((g, actions) -> Collections.unmodifiableSet(actions));

        return new Resolved(
                validRoles.isEmpty() ? List.of(BASE_ROLE) : List.copyOf(validRoles),
                Collections.unmodifiableMap(result)
        );
    }

    /** Khớp tên enum PatrolGroup, không phân biệt hoa thường; không khớp -> null. */
    private static PatrolGroup toGroup(String groupCode) {
        if (groupCode == null) return null;
        String g = groupCode.trim();
        for (PatrolGroup pg : PatrolGroup.values()) {
            if (pg.name().equalsIgnoreCase(g)) return pg;
        }
        return null;
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
