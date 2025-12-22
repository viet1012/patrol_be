package com.example.patrol_be.repository;

import com.example.patrol_be.model.PatrolGroupStt;
import com.example.patrol_be.model.AutoCmp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AutoCmpRepo
        extends JpaRepository<AutoCmp, Integer> {
    List<AutoCmp> findTop5ByLangAndNoteAndInputTextContainingOrderBySortOrderAsc(
            String lang,
            String note,
            String inputText);

    List<AutoCmp> findAllByLangAndNoteOrderBySortOrderAsc(String lang, String note);

    List<AutoCmp> findAllByOrderBySortOrderAsc();



}
