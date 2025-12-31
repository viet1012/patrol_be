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
        type,
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
        imageNames,
        createdAt,
        pic,
        dueDate,
        at_imageNames,
        at_comment,
        at_date,
        at_user,
        at_status,
        hse_judge,
        hse_imageNames,
        hse_comment,
        hse_date,
        load_status,
        patrol_user
    FROM F2_Patrol_Report
    WHERE (:type IS NULL OR LTRIM(RTRIM(type)) = LTRIM(RTRIM(:type)))
      AND (:grp IS NULL OR REPLACE(grp, ' ', '') LIKE '%' + REPLACE(:grp, ' ', '') + '%')
      AND (:plant IS NULL OR LTRIM(RTRIM(plant)) = LTRIM(RTRIM(:plant)))
      AND (:division IS NULL OR LTRIM(RTRIM(division)) LIKE '%' + LTRIM(RTRIM(:division)) + '%')
      AND (:area IS NULL OR LTRIM(RTRIM(area)) LIKE '%' + LTRIM(RTRIM(:area)) + '%')
      AND (:machine IS NULL OR LTRIM(RTRIM(machine)) LIKE '%' + LTRIM(RTRIM(:machine)) + '%')
      AND (:afStatus IS NULL OR ',' + :afStatus + ',' LIKE '%,' + LTRIM(RTRIM(at_status)) + ',%')
      AND (:pic IS NULL OR pic COLLATE Vietnamese_CI_AI LIKE '%' + :pic + '%')
      AND (:patrolUser IS NULL OR LTRIM(RTRIM(patrol_user)) LIKE '%' + LTRIM(RTRIM(:patrolUser)) + '%')
    ORDER BY stt DESC
""", nativeQuery = true)
    List<Object[]> search(
            @Param("plant") String plant,
            @Param("division") String division,
            @Param("area") String area,
            @Param("machine") String machine,
            @Param("type") String type,
            @Param("grp") String grp,
            @Param("afStatus") String afStatus,
            @Param("pic") String pic,
            @Param("patrolUser") String patrolUser
    );






    @Modifying
    @Query(value = """
    UPDATE F2_Patrol_Report
    SET
        at_imageNames = :imageNames,
        at_comment    = :comment,
        at_date       = :atDate,
        at_user        = :pic,
        at_status     = :status
    WHERE id = :id
    """, nativeQuery = true)
    int updateAtInfo(
            @Param("id") Long id,
            @Param("imageNames") String imageNames,
            @Param("comment") String comment,
            @Param("atDate") LocalDateTime atDate,
            @Param("pic") String pic,
            @Param("status") String status
    );

}