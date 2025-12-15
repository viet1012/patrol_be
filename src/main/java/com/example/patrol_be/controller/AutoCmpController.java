package com.example.patrol_be.controller;

import com.example.patrol_be.model.AutoCmp;
import com.example.patrol_be.service.AutoCmpService;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suggest")
@RequiredArgsConstructor
@CrossOrigin
public class AutoCmpController {
    private final AutoCmpService service;

    @GetMapping("/search")
    public List<AutoCmp> search(@RequestParam String q) {
        return service.search(q);
    }

    @GetMapping("/searchCounter")
    public List<AutoCmp> searchCounter(@RequestParam String q) {
        return service.searchCounter(q);
    }

    @GetMapping("/all")
    public List<AutoCmp> getAll() {
        return service.getAll();
    }
}
