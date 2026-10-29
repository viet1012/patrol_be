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
    public List<AutoCmp> search(@RequestParam String l,@RequestParam String q) {
        return service.search(l,q);
    }
    @GetMapping("/searchCounter")
    public List<AutoCmp> searchCounter(@RequestParam String l,@RequestParam String q) {
        return service.searchCounter(l,q);
    }

    @GetMapping("/comment")
    public List<AutoCmp> getAllComment(@RequestParam String l) {
        return service.getAllComment(l);
    }
    @GetMapping("/counter")
    public List<AutoCmp> getAllCounter(@RequestParam String l) {
        return service.getAllCounter(l);
    }

    @GetMapping("/all")
    public List<AutoCmp> getAll() {
        return service.getAll();
    }
}
