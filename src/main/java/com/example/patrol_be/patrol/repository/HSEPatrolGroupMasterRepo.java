package com.example.patrol_be.patrol.repository;

import com.example.patrol_be.patrol.entity.HSEPatrolGroupMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HSEPatrolGroupMasterRepo extends JpaRepository<HSEPatrolGroupMaster, Long> {

    Optional<HSEPatrolGroupMaster> findFirstByMacIdIgnoreCase(String macId);

    List<HSEPatrolGroupMaster> findByCateIgnoreCase(String cate);

    @Query(value = """
            SELECT 
                LTRIM(RTRIM(REPLACE(Plant, CHAR(160), ''))) AS plant,
                LTRIM(RTRIM(REPLACE(Grp, CHAR(160), ''))) AS fac,
                LTRIM(RTRIM(REPLACE(Area, CHAR(160), ''))) AS area,
                LTRIM(RTRIM(REPLACE(MacID, CHAR(160), ''))) AS macId,
                LTRIM(RTRIM(REPLACE(PIC, CHAR(160), ''))) AS pic
            FROM HSE_Patrol_Group_Master
            ORDER BY Plant, Grp, Area, MacID
            """, nativeQuery = true)
    List<Object[]> findAllMachines();

    @Query(
            value = """
            SELECT TOP (1)
                PIC
            FROM dbo.HSE_Patrol_Group_Master
            WHERE Plant = :plant
              AND Grp = :grp
              AND PIC IS NOT NULL
              AND PIC <> ''
            ORDER BY
                CASE
                    WHEN :area IS NOT NULL
                     AND :macId IS NOT NULL
                     AND Area = :area
                     AND MacId = :macId
                    THEN 1

                    WHEN :macId IS NOT NULL
                     AND MacId = :macId
                    THEN 2

                    WHEN :area IS NOT NULL
                     AND Area = :area
                    THEN 3

                    ELSE 4
                END
            """,
            nativeQuery = true
    )
    String resolvePic(
            @Param("plant") String plant,
            @Param("grp") String grp,
            @Param("area") String area,
            @Param("macId") String macId
    );


    @Query(value = """
    SELECT TOP 1 PIC
    FROM HSE_Patrol_Group_Master
    WHERE LTRIM(RTRIM(Plant)) = LTRIM(RTRIM(:plant))
      AND LTRIM(RTRIM(Grp)) = LTRIM(RTRIM(:grp))
      AND LTRIM(RTRIM(MacId)) = LTRIM(RTRIM(:macId))
    """, nativeQuery = true)
    String findPicByPlantGrpMac(
            @Param("plant") String plant,
            @Param("grp") String grp,
            @Param("macId") String macId
    );


    @Query(value = """
    SELECT TOP 1 PIC
    FROM HSE_Patrol_Group_Master
    WHERE UPPER(LTRIM(RTRIM(Plant))) = UPPER(LTRIM(RTRIM(:plant)))
      AND UPPER(LTRIM(RTRIM(Grp))) = UPPER(LTRIM(RTRIM(:grp)))
      AND UPPER(LTRIM(RTRIM(Area))) = UPPER(LTRIM(RTRIM(:area)))
      AND UPPER(LTRIM(RTRIM(MacId))) = UPPER(LTRIM(RTRIM(:macId)))
    """, nativeQuery = true)
    String findPicByPlantGrpAreaMac(
            @Param("plant") String plant,
            @Param("grp") String grp,
            @Param("area") String area,
            @Param("macId") String macId
    );

    @Query(value = """
                SELECT TOP 1 PIC
                FROM dbo.HSE_Patrol_Group_Master
                WHERE Plant = :plant
                  AND Grp   = :grp
                  AND Area  = :area
            """, nativeQuery = true)
    String findPicByPlantGrpArea(@Param("plant") String plant,
                                 @Param("grp") String grp,
                                 @Param("area") String area);

    @Query(value = """
                SELECT TOP 1 PIC
                FROM dbo.HSE_Patrol_Group_Master
                WHERE Plant = :plant
                  AND Grp   = :grp
            """, nativeQuery = true)
    String findPicByPlantGrp(@Param("plant") String plant,
                             @Param("grp") String grp);

    @Query(value = """
        SELECT 
            LTRIM(RTRIM(REPLACE(Plant, CHAR(160), ''))) AS plant,
            LTRIM(RTRIM(REPLACE(Grp, CHAR(160), ''))) AS fac,
            LTRIM(RTRIM(REPLACE(Area, CHAR(160), ''))) AS area,
            LTRIM(RTRIM(REPLACE(MacID, CHAR(160), ''))) AS macId,
            LTRIM(RTRIM(REPLACE(PIC, CHAR(160), ''))) AS pic
        FROM dbo.HSE_Patrol_Group_Master
        WHERE LTRIM(RTRIM(REPLACE(MacID, CHAR(160), ''))) = :macId
        ORDER BY Plant, Grp, Area
        """, nativeQuery = true)
    List<Object[]> findMachineInfoByMacId(@Param("macId") String macId);

    // ============================================================
    // GLOSSARY cho STT: division (Grp) + Area + MacID, distinct
    // :plant = NULL → lấy toàn bộ plant
    // ============================================================
    @Query(value = """
            ;WITH m AS (
                SELECT
                    LTRIM(RTRIM(REPLACE(Grp,   CHAR(160), ' '))) AS grp_n,
                    LTRIM(RTRIM(REPLACE(Area,  CHAR(160), ' '))) AS area_n,
                    LTRIM(RTRIM(REPLACE(MacID, CHAR(160), ' '))) AS mac_n
                FROM dbo.HSE_Patrol_Group_Master
                WHERE :plant IS NULL
                   OR UPPER(LTRIM(RTRIM(REPLACE(Plant, CHAR(160), ' ')))) = UPPER(LTRIM(RTRIM(:plant)))
            )
            , terms AS (
                SELECT grp_n AS term, 1 AS prio FROM m WHERE NULLIF(grp_n, '') IS NOT NULL
                UNION
                SELECT area_n, 2 FROM m WHERE NULLIF(area_n, '') IS NOT NULL
                UNION
                SELECT mac_n, 3 FROM m WHERE NULLIF(mac_n, '') IS NOT NULL
            )
            SELECT TOP (:limit) term
            FROM (
                SELECT term, MIN(prio) AS prio
                FROM terms
                GROUP BY term
            ) t
            ORDER BY prio, term
            """, nativeQuery = true)
    List<String> findGlossaryTerms(@Param("plant") String plant,
                                   @Param("limit") int limit);

    // ============================================================
    // PIC RESOLVE (dùng bởi PicResolver)
    // Tham số :plant, :grp, :area, :macId đã được trim + UPPER ở Java.
    // ============================================================

    /**
     * m: các dòng master thuộc Plant + Grp, cột đã chuẩn hóa (bỏ NBSP, trim, UPPER).
     */
    String PIC_MASTER_CTE = """
            ;WITH m AS (
                SELECT
                    UPPER(LTRIM(RTRIM(REPLACE(Cate,  CHAR(160), ' ')))) AS cate_n,
                    UPPER(LTRIM(RTRIM(REPLACE(Area,  CHAR(160), ' ')))) AS area_n,
                    UPPER(LTRIM(RTRIM(REPLACE(MacID, CHAR(160), ' ')))) AS mac_n,
                    LTRIM(RTRIM(REPLACE(PIC, CHAR(160), ' '))) AS pic_n
                FROM dbo.HSE_Patrol_Group_Master
                WHERE UPPER(LTRIM(RTRIM(REPLACE(Plant, CHAR(160), ' ')))) = :plant
                  AND UPPER(LTRIM(RTRIM(REPLACE(Grp,   CHAR(160), ' ')))) = :grp
            )
            """;

    /**
     * Chọn 1 PIC cố định từ matched(pic_n, is_general):
     * có dòng chung thì chỉ xét dòng chung, sau đó PIC xuất hiện nhiều nhất, rồi PIC ASC.
     */
    String PIC_PICK_ONE = """
            , candidates AS (
                SELECT pic_n
                FROM matched
                WHERE is_general = 1
                   OR NOT EXISTS (SELECT 1 FROM matched WHERE is_general = 1)
            )
            SELECT TOP (1) pic_n
            FROM candidates
            GROUP BY pic_n
            ORDER BY COUNT(*) DESC, pic_n ASC
            """;

    // a. MacID
    @Query(value = PIC_MASTER_CTE + """
            , matched AS (
                SELECT pic_n, 0 AS is_general
                FROM m
                WHERE mac_n = :macId
                  AND NULLIF(pic_n, '') IS NOT NULL
            )
            """ + PIC_PICK_ONE, nativeQuery = true)
    String findPicLevelMacId(@Param("plant") String plant,
                             @Param("grp") String grp,
                             @Param("macId") String macId);

    // b. Cate (Cate lấy từ dòng master của máy; dòng chung = MacID trống)
    @Query(value = PIC_MASTER_CTE + """
            , machine_cate AS (
                SELECT TOP (1) cate_n
                FROM m
                WHERE mac_n = :macId
                  AND NULLIF(cate_n, '') IS NOT NULL
                GROUP BY cate_n
                ORDER BY COUNT(*) DESC, cate_n ASC
            )
            , matched AS (
                SELECT pic_n,
                       CASE WHEN NULLIF(mac_n, '') IS NULL THEN 1 ELSE 0 END AS is_general
                FROM m
                WHERE cate_n = (SELECT cate_n FROM machine_cate)
                  AND NULLIF(pic_n, '') IS NOT NULL
            )
            """ + PIC_PICK_ONE, nativeQuery = true)
    String findPicLevelCate(@Param("plant") String plant,
                            @Param("grp") String grp,
                            @Param("macId") String macId);

    // c. Area (dòng chung = MacID và Cate trống)
    @Query(value = PIC_MASTER_CTE + """
            , matched AS (
                SELECT pic_n,
                       CASE WHEN NULLIF(mac_n, '') IS NULL
                             AND NULLIF(cate_n, '') IS NULL
                            THEN 1 ELSE 0 END AS is_general
                FROM m
                WHERE area_n = :area
                  AND NULLIF(pic_n, '') IS NOT NULL
            )
            """ + PIC_PICK_ONE, nativeQuery = true)
    String findPicLevelArea(@Param("plant") String plant,
                            @Param("grp") String grp,
                            @Param("area") String area);

    // d. Grp (dòng chung = MacID, Cate và Area trống)
    @Query(value = PIC_MASTER_CTE + """
            , matched AS (
                SELECT pic_n,
                       CASE WHEN NULLIF(mac_n, '') IS NULL
                             AND NULLIF(cate_n, '') IS NULL
                             AND NULLIF(area_n, '') IS NULL
                            THEN 1 ELSE 0 END AS is_general
                FROM m
                WHERE NULLIF(pic_n, '') IS NOT NULL
            )
            """ + PIC_PICK_ONE, nativeQuery = true)
    String findPicLevelGrp(@Param("plant") String plant,
                           @Param("grp") String grp);
}