package com.example.patrol_be.repository;

import com.example.patrol_be.model.PatrolGroupStt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PatrolGroupSttRepo extends JpaRepository<PatrolGroupStt, Long> {

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
            SELECT p
            FROM PatrolGroupStt p
            WHERE p.fac = :fac
              AND p.type = :type
        """)
        Optional<PatrolGroupStt> findForUpdateByFacAndType(
                @Param("fac") String fac,
                @Param("type") String type
        );

        @Query("""
            SELECT p.currentStt
            FROM PatrolGroupStt p
            WHERE p.fac = :fac
              AND p.type = :type
        """)
        Optional<Integer> getCurrentByFacAndType(
                @Param("fac") String fac,
                @Param("type") String type
        );


}