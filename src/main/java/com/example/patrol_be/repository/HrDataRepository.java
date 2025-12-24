package com.example.patrol_be.repository;

import com.example.patrol_be.model.HrData;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HrDataRepository extends JpaRepository<HrData, Integer> {

    @Query(value = "SELECT TOP 1 Name FROM F2_HR_Data WHERE Code = :code", nativeQuery = true)
    String findNameByCode(@Param("code") String code);
}
