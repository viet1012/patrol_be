package com.example.patrol_be.repository;

import com.example.patrol_be.model.PatrolReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatrolReportRepo extends JpaRepository<PatrolReport, Long> {}