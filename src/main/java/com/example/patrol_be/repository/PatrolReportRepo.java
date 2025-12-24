package com.example.patrol_be.repository;

import com.example.patrol_be.model.PatrolReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.*;

import java.time.LocalDateTime;
import java.util.List;

public interface PatrolReportRepo extends JpaRepository<PatrolReport, Long> {

    @Query(value = """
    SELECT
        id,
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
        dueDate,
        imageNames
    FROM F2_Patrol_Report
    WHERE (:type IS NULL OR LTRIM(RTRIM(type)) = LTRIM(RTRIM(:type)))
      AND  (:division IS NULL OR LTRIM(RTRIM(division)) LIKE LTRIM(RTRIM(:division)))
      AND (:area IS NULL OR LTRIM(RTRIM(area)) LIKE LTRIM(RTRIM(:area)))
      AND (:machine IS NULL OR LTRIM(RTRIM(machine)) LIKE LTRIM(RTRIM(:machine)))
    ORDER BY stt DESC
""", nativeQuery = true)
    List<Object[]> search(
            @Param("division") String division,
            @Param("area") String area,
            @Param("machine") String machine,
            @Param("type") String type

    );


    @Modifying
    @Query(value = """
        UPDATE F2_Patrol_Report
        SET 
            at_imageNames = :imageNames,
            at_comment    = :comment,
            at_date       = :date,
            at_pic        = :pic,
            at_status     = :status
        WHERE id = :id
        """, nativeQuery = true)
    int updateAtInfo(
            @Param("id") Long id,
            @Param("imageNames") String imageNames,
            @Param("comment") String comment,
            @Param("date") LocalDateTime date,
            @Param("pic") String pic,
            @Param("status") String status
    );
}