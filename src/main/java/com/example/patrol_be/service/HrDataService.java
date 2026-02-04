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

        String role = normalizeRole(emp.getRole());

        return new AuthMeResponse(
                emp.getEmpId(),
                emp.getEmpName(),
                emp.getPlant(),
                role,
                buildPermissions(role)
        );
    }

    private String normalizeRole(String role) {
        if (role == null) return "User";
        role = role.trim().toUpperCase();
        if (role.equals("ADMIN")) return "Admin";
        if (role.equals("HSE")) return "HSE";
        if (role.equals("QA")) return "QA";
        return "User";
    }

    /**
     * RULE QUYỀN – CHỈ 1 CHỖ DUY NHẤT
     */
    private Map<String, Map<String, Boolean>> buildPermissions(String role) {

        Map<String, Map<String, Boolean>> p = new HashMap<>();

        // init all false
        for (String g : List.of("Patrol", "Audit", "QualityPatrol")) {
            p.put(g, action(false, false, false, false));
        }

        switch (role) {

            case "Admin" -> {
                for (String g : p.keySet()) {
                    p.put(g, action(true, true, true, true));
                }
            }

            case "HSE" -> {
                p.put("Patrol", action(true, true, true, true));
                p.put("Audit", action(true, true, true, true));
                p.put("QualityPatrol", action(false, false, false, true));
            }

            case "QA" -> {
                p.put("QualityPatrol", action(true, true, true, true));
                p.put("Patrol", action(true, true, false, true));
                p.put("Audit", action(false, false, false, true));
            }

            default -> { // User
                p.put("Patrol", action(true, true, false, true));
            }
        }

        return p;
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
