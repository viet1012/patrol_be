package com.example.patrol_be.repository;

import com.example.patrol_be.model.PatrolReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.*;

import java.util.List;

public interface PatrolReportRepo extends JpaRepository<PatrolReport, Long> {

    @Query(value = """
    SELECT 
        stt,
        grp,
        plant,
        division,
        area,
        machine,
        riskFreq,
        riskProb,
        riskSev,
        riskTotal,
        comment,
        countermeasure,
        checkInfo,
        imageNames
    FROM F2_Patrol_Report
    WHERE (:division IS NULL OR LTRIM(RTRIM(division)) LIKE LTRIM(RTRIM(:division)))
      AND (:area IS NULL OR LTRIM(RTRIM(area)) LIKE LTRIM(RTRIM(:area)))
      AND (:machine IS NULL OR LTRIM(RTRIM(machine)) LIKE LTRIM(RTRIM(:machine)))
    ORDER BY stt DESC
""", nativeQuery = true)
    List<Object[]> search(
            @Param("division") String division,
            @Param("area") String area,
            @Param("machine") String machine
    );

}