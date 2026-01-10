package com.example.patrol_be.repository;
import com.example.patrol_be.model.HSEPatrolGroupMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
public interface HSEPatrolGroupMasterRepo extends JpaRepository<HSEPatrolGroupMaster, Long> {

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

    @Query(value = """
        SELECT TOP 1 LTRIM(RTRIM(REPLACE(PIC, CHAR(160), ''))) AS pic
        FROM HSE_Patrol_Group_Master
        WHERE LTRIM(RTRIM(REPLACE(Plant, CHAR(160), ''))) = :plant
            AND LTRIM(RTRIM(REPLACE(MacID, CHAR(160), ''))) = :macId
        """, nativeQuery = true)
    String findPIC(@Param("plant") String plant,
                                    @Param("macId") String macId);
}