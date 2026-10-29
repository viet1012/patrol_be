package com.example.patrol_be.service;


import com.example.patrol_be.model.HsePatrolTeam;
import com.example.patrol_be.repository.HsePatrolTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HsePatrolTeamService {

    private final HsePatrolTeamRepository repo;

    public List<HsePatrolTeam> getAll() {
        return repo.findAll();
    }
}
