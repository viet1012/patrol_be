package com.example.patrol_be.service;

import com.example.patrol_be.dto.AuthMeResponse;
import com.example.patrol_be.permission.PatrolAction;
import com.example.patrol_be.permission.PatrolGroup;
import com.example.patrol_be.permission.PermissionService;
import com.example.patrol_be.repository.HrDataRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class HrDataService {
    private final HrDataRepository hrDataRepository;
    private final PermissionService permissionService;

    public HrDataService(HrDataRepository hrDataRepository, PermissionService permissionService) {
        this.hrDataRepository = hrDataRepository;
        this.permissionService = permissionService;
    }

    public String getNameByCode(String code) {
        return hrDataRepository.findNameByCode(code);
    }

    public AuthMeResponse getMe(String code) {

        var emp = hrDataRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        PermissionService.Resolved resolved = permissionService.resolveWithRoles(emp.getRole());

        return new AuthMeResponse(
                emp.getEmpId(),
                emp.getEmpName(),
                emp.getPlant(),
                String.join(",", resolved.roles()),
                toPermissionMap(resolved.permissions())
        );
    }

    /**
     * Giữ format JSON cũ: { "<Group>": { "before": bool, "after": bool, "recheck": bool, "summary": bool } },
     * đủ mọi group và mọi action.
     */
    private Map<String, Map<String, Boolean>> toPermissionMap(Map<PatrolGroup, Set<PatrolAction>> perms) {

        Map<String, Map<String, Boolean>> p = new LinkedHashMap<>();

        for (PatrolGroup g : PatrolGroup.values()) {
            Set<PatrolAction> granted = perms.getOrDefault(g, Set.of());

            Map<String, Boolean> actions = new LinkedHashMap<>();
            for (PatrolAction a : PatrolAction.values()) {
                actions.put(a.key(), granted.contains(a));
            }

            p.put(g.name(), actions);
        }

        return p;
    }

}
