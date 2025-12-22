package com.example.patrol_be.repository;

import com.example.patrol_be.model.AutoCmp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatrolCommentRepo extends JpaRepository<AutoCmp, Integer> {
    @Query(value = """
        SELECT TOP 1 pa.InputText
        FROM HSE_Patrol_Comment pa
        INNER JOIN (
            SELECT Sort_Order,
                   CASE 
                       WHEN Lang = 'JP' THEN 'VI'
                       ELSE 'JP'
                   END AS Lang
            FROM HSE_Patrol_Comment
            WHERE InputText = :inputText
        ) tb
        ON pa.Sort_Order = tb.Sort_Order
        AND pa.Lang = tb.Lang
        """, nativeQuery = true)
    Optional<String> findTranslatedText(@Param("inputText") String inputText);
}
