package com.example.patrol_be.repository;

import com.example.patrol_be.model.HsePatrolTeam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HsePatrolTeamRepository
        extends JpaRepository<HsePatrolTeam, Long> {
}
