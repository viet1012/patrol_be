package com.example.patrol_be.repository;

import com.example.patrol_be.model.HseEmp;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HrDataRepository extends JpaRepository<HseEmp, Integer> {

    @Query(value = "SELECT TOP 1 EmpName FROM HSE_EmpID WHERE EmpID = :code", nativeQuery = true)
    String findNameByCode(@Param("code") String code);
    boolean existsByEmpId(String empId);
}
