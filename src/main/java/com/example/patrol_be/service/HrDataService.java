package com.example.patrol_be.service;

import com.example.patrol_be.dto.AuthMeResponse;
import com.example.patrol_be.repository.HrDataRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HrDataService {
    private final HrDataRepository hrDataRepository;

    public HrDataService(HrDataRepository hrDataRepository) {
        this.hrDataRepository = hrDataRepository;
    }

    public String getNameByCode(String code) {
        return hrDataRepository.findNameByCode(code);
    }

    public AuthMeResponse getMe(String code) {

        var emp = hrDataRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        List<String> roles = normalizeRoles(emp.getRole());

        return new AuthMeResponse(
                emp.getEmpId(),
                emp.getEmpName(),
                emp.getPlant(),
                String.join(",", roles),
                buildPermissions(roles)
        );
    }

    private List<String> normalizeRoles(String role) {
        if (role == null || role.isBlank()) return List.of("User");

        return List.of(role.split(","))
                .stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .map(r -> switch (r) {
                    case "ADMIN" -> "Admin";
                    case "HSE" -> "HSE";
                    case "QA" -> "QA";
                    default -> "User";
                })
                .distinct()
                .toList();
    }

    /**
     * RULE QUYỀN – CHỈ 1 CHỖ DUY NHẤT
     */
    private Map<String, Map<String, Boolean>> buildPermissions(List<String> roles) {

        Map<String, Map<String, Boolean>> p = new HashMap<>();

        for (String g : List.of("Patrol", "Audit", "QualityPatrol", "AssetUpdate")) {
            p.put(g, action(false, false, false, false));
        }

        for (String role : roles) {
            switch (role) {

                case "Admin" -> {
                    for (String g : p.keySet()) {
                        p.put(g, merge(p.get(g), action(true, true, false, true)));
                    }
                }

                case "HSE" -> {
                    p.put("Patrol", merge(p.get("Patrol"), action(true, true, true, true)));
                    p.put("Audit", merge(p.get("Audit"), action(true, true, true, true)));
                    p.put("QualityPatrol", merge(p.get("QualityPatrol"), action(false, false, false, true)));
                }

                case "QA" -> {
                    p.put("QualityPatrol", merge(p.get("QualityPatrol"), action(true, true, true, true)));
                    p.put("Patrol", merge(p.get("Patrol"), action(true, true, false, true)));
                    p.put("Audit", merge(p.get("Audit"), action(false, false, false, true)));
                }

                default -> {
                    p.put("Patrol", merge(p.get("Patrol"), action(true, true, false, true)));
                }
            }
        }

        return p;
    }
    private Map<String, Boolean> merge(
            Map<String, Boolean> oldPerm,
            Map<String, Boolean> newPerm
    ) {
        return Map.of(
                "before", oldPerm.getOrDefault("before", false) || newPerm.getOrDefault("before", false),
                "after", oldPerm.getOrDefault("after", false) || newPerm.getOrDefault("after", false),
                "recheck", oldPerm.getOrDefault("recheck", false) || newPerm.getOrDefault("recheck", false),
                "summary", oldPerm.getOrDefault("summary", false) || newPerm.getOrDefault("summary", false)
        );
    }
    private Map<String, Boolean> action(
            boolean before,
            boolean after,
            boolean recheck,
            boolean summary
    ) {
        return Map.of(
                "before", before,
                "after", after,
                "recheck", recheck,
                "summary", summary
        );
    }

}
