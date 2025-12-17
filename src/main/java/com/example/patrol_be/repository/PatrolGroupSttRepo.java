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

//public interface PatrolGroupSttRepo extends JpaRepository<PatrolGroupStt, Long> {
//
//        Optional<PatrolGroupStt> findByWorkDateAndFacAndGrp(
//                LocalDate workDate,
//                String fac,
//                String grp
//        );
//
//        Optional<PatrolGroupStt> findByFacAndGrp(
//                String fac,
//                String grp
//        );
//
//        List<PatrolGroupStt> findByFac(String fac);
//
//        @Lock(LockModeType.PESSIMISTIC_WRITE)
//        @Query("""
//            SELECT MAX(p.currentStt)
//            FROM PatrolGroupStt p
//            WHERE p.fac = :fac
//        """)
//        Integer findMaxSttForUpdate(@Param("fac") String fac);
//
//        @Query("""
//            SELECT p
//            FROM PatrolGroupStt p
//            WHERE p.fac = :fac
//            ORDER BY p.id DESC
//        """)
//        List<PatrolGroupStt> findLatestByFac(@Param("fac") String fac);
//
//        @Query("""
//            SELECT COALESCE(SUM(p.currentStt), 0)
//            FROM PatrolGroupStt p
//            WHERE p.fac = :fac
//        """)
//        int sumCurrentSttByFac(@Param("fac") String fac);
//
//}
public interface PatrolGroupSttRepo extends JpaRepository<PatrolGroupStt, Long> {

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
            SELECT p
            FROM PatrolGroupStt p
            WHERE p.fac = :fac
        """)
        Optional<PatrolGroupStt> findForUpdateByFac(@Param("fac") String fac);

        @Query("""
            SELECT p.currentStt
            FROM PatrolGroupStt p
            WHERE p.fac = :fac
        """)
        Optional<Integer> getCurrentByFac(@Param("fac") String fac);

}