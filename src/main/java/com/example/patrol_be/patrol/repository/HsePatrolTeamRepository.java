package com.example.patrol_be.patrol.repository;

import com.example.patrol_be.patrol.entity.HsePatrolTeam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HsePatrolTeamRepository
        extends JpaRepository<HsePatrolTeam, Long> {
}
