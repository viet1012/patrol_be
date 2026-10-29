package com.example.patrol_be.patrol.controller;

import com.example.patrol_be.patrol.entity.HsePatrolTeam;
import com.example.patrol_be.patrol.service.HsePatrolTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patrol_teams")
@RequiredArgsConstructor
@CrossOrigin
public class HsePatrolTeamController {

    private final HsePatrolTeamService service;

    @GetMapping
    public List<HsePatrolTeam> getAll() {
        return service.getAll();
    }
}
