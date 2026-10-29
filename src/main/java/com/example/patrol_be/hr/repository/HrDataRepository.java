package com.example.patrol_be.hr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.patrol_be.hr.entity.HseEmp;

@Repository
public interface HrDataRepository extends JpaRepository<HseEmp, String> {

    @Query(value = "SELECT TOP 1 EmpName FROM HSE_EmpID WHERE EmpID = :code", nativeQuery = true)
    String findNameByCode(@Param("code") String code);
    boolean existsByEmpId(String empId);

    @Query("SELECT e FROM HseEmp e WHERE e.empId = :code")
    Optional<HseEmp> findByCode(@Param("code") String code);
}
