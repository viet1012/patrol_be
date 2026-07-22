package com.example.patrol_be.repository;

import com.example.patrol_be.model.HSEPatrolGroupMaster;
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
}