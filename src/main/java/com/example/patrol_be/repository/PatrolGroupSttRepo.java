package com.example.patrol_be.repository;

import com.example.patrol_be.model.PatrolGroupStt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PatrolGroupSttRepo extends JpaRepository<PatrolGroupStt, Long> {

        Optional<PatrolGroupStt> findByWorkDateAndFacAndGrp(
                LocalDate workDate,
                String fac,
                String grp
        );

}