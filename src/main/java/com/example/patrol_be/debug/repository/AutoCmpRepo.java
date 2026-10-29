package com.example.patrol_be.debug.repository;

import com.example.patrol_be.media.entity.PatrolGroupStt;
import com.example.patrol_be.debug.entity.AutoCmp;
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
