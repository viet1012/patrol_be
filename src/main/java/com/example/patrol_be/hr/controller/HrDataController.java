package com.example.patrol_be.controller;

import com.example.patrol_be.dto.AuthMeResponse;
import com.example.patrol_be.service.HrDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/hr")
public class HrDataController {
    private final HrDataService hrDataService;

    public HrDataController(HrDataService hrDataService) {
        this.hrDataService = hrDataService;
    }

    @GetMapping("/name")
    public ResponseEntity<String> getNameByCode(@RequestParam String code) {
        String name = hrDataService.getNameByCode(code);
        if (name != null) {
            return ResponseEntity.ok(name);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/me")
    public AuthMeResponse me(
            @RequestParam("code") String code
    ) {
        return hrDataService.getMe(code.trim());
    }


    @GetMapping("/recheck")
    public ResponseEntity<?> recheck(
            @RequestParam String code,
            @RequestParam String group
    ) {
        var me = hrDataService.getMe(code);

        boolean allowed = me.getPermissions()
                .getOrDefault(group, Map.of())
                .getOrDefault("recheck", false);

        if (!allowed) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // xử lý tiếp
        return ResponseEntity.ok("OK");
    }


}
