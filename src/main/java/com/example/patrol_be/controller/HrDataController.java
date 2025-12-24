package com.example.patrol_be.controller;

import com.example.patrol_be.service.HrDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
