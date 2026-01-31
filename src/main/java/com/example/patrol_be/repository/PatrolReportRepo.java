package com.example.patrol_be.repository;

import com.example.patrol_be.model.PatrolReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
                    patrol_user,
                    qr_key
                FROM F2_Patrol_Report
                WHERE (:type IS NULL OR LTRIM(RTRIM(type)) = LTRIM(RTRIM(:type)))
                  AND (:grp IS NULL OR REPLACE(grp, ' ', '') LIKE '%' + REPLACE(:grp, ' ', '') + '%')
                  AND (:plant IS NULL OR LTRIM(RTRIM(plant)) = LTRIM(RTRIM(:plant)))
                  AND (:division IS NULL OR LTRIM(RTRIM(division)) LIKE '%' + LTRIM(RTRIM(:division)) + '%')
                  AND (:area IS NULL OR LTRIM(RTRIM(area)) LIKE '%' + LTRIM(RTRIM(:area)) + '%')
                  AND (:machine IS NULL OR LTRIM(RTRIM(machine)) LIKE '%' + LTRIM(RTRIM(:machine)) + '%')
                  AND (:afStatus IS NULL OR ',' + :afStatus + ',' LIKE '%,' + LTRIM(RTRIM(at_status)) + ',%')
                  AND (
                    :pic IS NULL
                    OR (LTRIM(RTRIM(:pic)) = '' AND (pic IS NULL OR LTRIM(RTRIM(pic)) = ''))
                    OR (LTRIM(RTRIM(:pic)) <> '' AND LTRIM(RTRIM(pic)) COLLATE Vietnamese_CI_AI LIKE '%' + LTRIM(RTRIM(:pic)) + '%')
                       )
                  AND (:patrolUser IS NULL OR LTRIM(RTRIM(patrol_user)) LIKE '%' + LTRIM(RTRIM(:patrolUser)) + '%')
                  AND (:qr_key IS NULL OR LTRIM(RTRIM(qr_key)) = LTRIM(RTRIM(:qr_key)))
                  AND (
                      :fromD IS NULL OR createdAt >= :fromD
                    )
                  AND (
                      :toD IS NULL OR createdAt < DATEADD(DAY, 1, :toD)
                    )
            
                ORDER BY stt DESC
                OPTION (RECOMPILE);
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
            @Param("patrolUser") String patrolUser,
            @Param("qr_key") String qr_key,
            @Param("fromD") LocalDate fromD,
            @Param("toD") LocalDate toD
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


    @Modifying
    @Query(value = """
            UPDATE F2_Patrol_Report
            SET
                hse_imageNames = :imageNames,
                hse_comment    = :comment,
                hse_date       = :hseDate,
                hse_user       = :user,
                hse_judge      = :judge,
                at_status     = :status
            WHERE id = :id
            """, nativeQuery = true)
    int updateHseInfo(
            @Param("id") Long id,
            @Param("imageNames") String imageNames,
            @Param("comment") String comment,
            @Param("hseDate") LocalDateTime hseDate,   // ✅ LocalDate
            @Param("user") String user,
            @Param("judge") String judge,
            @Param("status") String status

    );


    @Query(value = """
                SELECT
                    COALESCE(NULLIF(LTRIM(RTRIM(pic)), ''), 'UNKNOWN') AS pic,
                    SUM(CASE WHEN LTRIM(RTRIM(riskTotal)) = 'I'   THEN 1 ELSE 0 END) AS i,
                    SUM(CASE WHEN LTRIM(RTRIM(riskTotal)) = 'II'  THEN 1 ELSE 0 END) AS ii,
                    SUM(CASE WHEN LTRIM(RTRIM(riskTotal)) = 'III' THEN 1 ELSE 0 END) AS iii,
                    SUM(CASE WHEN LTRIM(RTRIM(riskTotal)) = 'IV'  THEN 1 ELSE 0 END) AS iv,
                    SUM(CASE WHEN LTRIM(RTRIM(riskTotal)) = 'V'   THEN 1 ELSE 0 END) AS v,
                    COUNT(*) AS total
                FROM F2_Patrol_Report
                WHERE LTRIM(RTRIM(plant)) = LTRIM(RTRIM(:plant))
                  AND LTRIM(RTRIM(at_status)) IN (:atStatuses)
                  AND LTRIM(RTRIM(type))  = LTRIM(RTRIM(:type))
                GROUP BY COALESCE(NULLIF(LTRIM(RTRIM(pic)), ''), 'UNKNOWN')
                ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> pivotByPicAndRisk(
            @Param("plant") String plant,
            @Param("atStatuses") List<String> atStatuses,
            @Param("type") String type);

    @Query(
            value = """
                        SELECT
                                CASE
                                      WHEN GROUPING(grp) = 1 THEN 'TOTAL'
                                      ELSE grp
                                  END AS grp,
                    
                                  CASE\s
                                      WHEN GROUPING(division) = 1 THEN ''
                                      ELSE division
                                  END AS division,
                        
                                  SUM(CASE WHEN riskTotal IS NULL OR riskTotal='' THEN 1 ELSE 0 END) AS [-],
                                  SUM(CASE WHEN riskTotal = 'I'   THEN 1 ELSE 0 END) AS [I],
                                  SUM(CASE WHEN riskTotal = 'II'  THEN 1 ELSE 0 END) AS [II],
                                  SUM(CASE WHEN riskTotal = 'III' THEN 1 ELSE 0 END) AS [III],
                                  SUM(CASE WHEN riskTotal = 'IV'  THEN 1 ELSE 0 END) AS [IV],
                                  SUM(CASE WHEN riskTotal = 'V'   THEN 1 ELSE 0 END) AS [V]
                        FROM F2_Patrol_Report
                            WHERE createdAt >= :fromD
                              AND createdAt <  DATEADD(DAY, 1, :toD)
                              AND [type] = :type
                              AND plant = :fac
                            GROUP BY ROLLUP (grp, division)
                        
                            -- ❌ bỏ subtotal theo grp, chỉ giữ detail + TOTAL
                            HAVING NOT (GROUPING(division)=1 AND GROUPING(grp)=0)
                        
                            ORDER BY
                              CASE WHEN GROUPING(grp)=1 AND GROUPING(division)=1 THEN 1 ELSE 0 END,
                              grp, division;
                    
                    """,
            nativeQuery = true
    )
    List<Object[]> summaryRiskRaw(
            @Param("fromD") LocalDate fromD,
            @Param("toD") LocalDate toD,
            @Param("fac") String fac,
            @Param("type") String type
    );

    @Query(value = """
        SELECT
            division,

            COUNT(1) AS All_TTL,
            SUM(CASE WHEN riskTotal IN ('-', 'I')   THEN 1 ELSE 0 END) AS All_I,
            SUM(CASE WHEN riskTotal = 'II'  THEN 1 ELSE 0 END) AS All_II,
            SUM(CASE WHEN riskTotal = 'III' THEN 1 ELSE 0 END) AS All_III,
            SUM(CASE WHEN riskTotal = 'IV'  THEN 1 ELSE 0 END) AS All_IV,
            SUM(CASE WHEN riskTotal = 'V'   THEN 1 ELSE 0 END) AS All_V,

            SUM(CASE WHEN LTRIM(RTRIM(UPPER(ISNULL(at_status,'')))) IN ('DONE','COMPLETED') THEN 1 ELSE 0 END) AS Pro_Done_TTL,
            SUM(CASE WHEN LTRIM(RTRIM(UPPER(ISNULL(at_status,'')))) IN ('DONE','COMPLETED') AND riskTotal IN ('-', 'I')   THEN 1 ELSE 0 END) AS Pro_Done_I,
            SUM(CASE WHEN LTRIM(RTRIM(UPPER(ISNULL(at_status,'')))) IN ('DONE','COMPLETED') AND riskTotal = 'II'  THEN 1 ELSE 0 END) AS Pro_Done_II,
            SUM(CASE WHEN LTRIM(RTRIM(UPPER(ISNULL(at_status,'')))) IN ('DONE','COMPLETED') AND riskTotal = 'III' THEN 1 ELSE 0 END) AS Pro_Done_III,
            SUM(CASE WHEN LTRIM(RTRIM(UPPER(ISNULL(at_status,'')))) IN ('DONE','COMPLETED') AND riskTotal = 'IV'  THEN 1 ELSE 0 END) AS Pro_Done_IV,
            SUM(CASE WHEN LTRIM(RTRIM(UPPER(ISNULL(at_status,'')))) IN ('DONE','COMPLETED') AND riskTotal = 'V'   THEN 1 ELSE 0 END) AS Pro_Done_V,

            SUM(CASE WHEN at_status = 'Completed' THEN 1 ELSE 0 END) AS HSE_Done_TTL,
            SUM(CASE WHEN at_status = 'Completed' AND riskTotal IN ('-', 'I')   THEN 1 ELSE 0 END) AS HSE_Done_I,
            SUM(CASE WHEN at_status = 'Completed' AND riskTotal = 'II'  THEN 1 ELSE 0 END) AS HSE_Done_II,
            SUM(CASE WHEN at_status = 'Completed' AND riskTotal = 'III' THEN 1 ELSE 0 END) AS HSE_Done_III,
            SUM(CASE WHEN at_status = 'Completed' AND riskTotal = 'IV'  THEN 1 ELSE 0 END) AS HSE_Done_IV,
            SUM(CASE WHEN at_status = 'Completed' AND riskTotal = 'V'   THEN 1 ELSE 0 END) AS HSE_Done_V,
            -- ===== REMAIN =====
            SUM(CASE WHEN at_status IN ('Wait','Redo') THEN 1 ELSE 0 END) AS [Remain_TTL],
            SUM(CASE WHEN at_status IN ('Wait','Redo') AND riskTotal IN ('-', 'I')   THEN 1 ELSE 0 END) AS [Remain_I],
            SUM(CASE WHEN at_status IN ('Wait','Redo') AND riskTotal = 'II'  THEN 1 ELSE 0 END) AS [Remain_II],
            SUM(CASE WHEN at_status IN ('Wait','Redo') AND riskTotal = 'III' THEN 1 ELSE 0 END) AS [Remain_III],
            SUM(CASE WHEN at_status IN ('Wait','Redo') AND riskTotal = 'IV'  THEN 1 ELSE 0 END) AS [Remain_IV],
            SUM(CASE WHEN at_status IN ('Wait','Redo') AND riskTotal = 'V'   THEN 1 ELSE 0 END) AS [Remain_V]

        FROM dbo.F2_Patrol_Report
        WHERE createdAt >= :fromD
          AND createdAt < DATEADD(DAY, 1, :toD)
          AND [type] = :type
          AND plant = :fac
        GROUP BY division
        ORDER BY division
        """, nativeQuery = true)
    List<Object[]> summaryByDivisionRaw(
            @Param("fromD") LocalDate fromD,
            @Param("toD") LocalDate toD,
            @Param("fac") String fac,
            @Param("type") String type
    );

    @Query(value = """
            SELECT
                pic,
            
                COUNT(1) AS allTtl,
            
                CAST(ROUND(
                    100.0 * SUM(CASE WHEN at_status = 'Wait' THEN 1 ELSE 0 END)
                    / NULLIF(COUNT(1), 0), 0
                ) AS INT) AS allNyPct,
            
                SUM(CASE WHEN at_status IN ('Done','Completed') THEN 1 ELSE 0 END) AS allOk,
                SUM(CASE WHEN at_status = 'Redo' THEN 1 ELSE 0 END) AS allNg,
                SUM(CASE WHEN at_status = 'Wait' THEN 1 ELSE 0 END) AS allNy,
            
                -- ===== Fac_A =====
                SUM(CASE WHEN division='Fac_A' THEN 1 ELSE 0 END) AS facATtl,
                CAST(ROUND(
                    100.0 * SUM(CASE WHEN at_status='Wait' AND division='Fac_A' THEN 1 ELSE 0 END)
                    / NULLIF(SUM(CASE WHEN division='Fac_A' THEN 1 ELSE 0 END), 0), 0
                ) AS INT) AS facANyPct,
                SUM(CASE WHEN at_status IN ('Done','Completed') AND division='Fac_A' THEN 1 ELSE 0 END) AS facAOk,
                SUM(CASE WHEN at_status = 'Redo' AND division='Fac_A' THEN 1 ELSE 0 END) AS facANg,
                SUM(CASE WHEN at_status = 'Wait' AND division='Fac_A' THEN 1 ELSE 0 END) AS facANy,
            
                -- ===== Fac_B =====
                SUM(CASE WHEN division='Fac_B' THEN 1 ELSE 0 END) AS facBTtl,
                CAST(ROUND(
                    100.0 * SUM(CASE WHEN at_status='Wait' AND division='Fac_B' THEN 1 ELSE 0 END)
                    / NULLIF(SUM(CASE WHEN division='Fac_B' THEN 1 ELSE 0 END), 0), 0
                ) AS INT) AS facBNyPct,
                SUM(CASE WHEN at_status IN ('Done','Completed') AND division='Fac_B' THEN 1 ELSE 0 END) AS facBOk,
                SUM(CASE WHEN at_status = 'Redo' AND division='Fac_B' THEN 1 ELSE 0 END) AS facBNg,
                SUM(CASE WHEN at_status = 'Wait' AND division='Fac_B' THEN 1 ELSE 0 END) AS facBNy,
            
                -- ===== Fac_C =====
                SUM(CASE WHEN division='Fac_C' THEN 1 ELSE 0 END) AS facCTtl,
                CAST(ROUND(
                    100.0 * SUM(CASE WHEN at_status='Wait' AND division='Fac_C' THEN 1 ELSE 0 END)
                    / NULLIF(SUM(CASE WHEN division='Fac_C' THEN 1 ELSE 0 END), 0), 0
                ) AS INT) AS facCNyPct,
                SUM(CASE WHEN at_status IN ('Done','Completed') AND division='Fac_C' THEN 1 ELSE 0 END) AS facCOk,
                SUM(CASE WHEN at_status = 'Redo' AND division='Fac_C' THEN 1 ELSE 0 END) AS facCNg,
                SUM(CASE WHEN at_status = 'Wait' AND division='Fac_C' THEN 1 ELSE 0 END) AS facCNy,
            
                -- ===== Outside =====
                SUM(CASE WHEN division LIKE 'Outside%%' THEN 1 ELSE 0 END) AS outsideTtl,
                CAST(ROUND(
                    100.0 * SUM(CASE WHEN at_status='Wait' AND division LIKE 'Outside%%' THEN 1 ELSE 0 END)
                    / NULLIF(SUM(CASE WHEN division LIKE 'Outside%%' THEN 1 ELSE 0 END), 0), 0
                ) AS INT) AS outsideNyPct,
                SUM(CASE WHEN at_status IN ('Done','Completed') AND division LIKE 'Outside%%' THEN 1 ELSE 0 END) AS outsideOk,
                SUM(CASE WHEN at_status = 'Redo' AND division LIKE 'Outside%%' THEN 1 ELSE 0 END) AS outsideNg,
                SUM(CASE WHEN at_status = 'Wait' AND division LIKE 'Outside%%' THEN 1 ELSE 0 END) AS outsideNy
            
            FROM F2_Patrol_Report
            WHERE createdAt >= :fromD
              AND createdAt < DATEADD(day, 1, :toD)   -- ✅ bao gồm cả ngày toD
              AND [type] = :type
              AND plant = :fac
              AND riskTotal IN (:lvls)
            GROUP BY pic
            ORDER BY allTtl DESC
            """, nativeQuery = true)
    List<Object[]> fetchPicSummaryRaw(
            @Param("fromD") LocalDate fromD,
            @Param("toD") LocalDate toD,
            @Param("fac") String fac,
            @Param("type") String type,
            @Param("lvls") List<String> lvls
    );
}