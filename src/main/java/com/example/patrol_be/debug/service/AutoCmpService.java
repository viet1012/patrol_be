package com.example.patrol_be.debug.service;

import com.example.patrol_be.debug.repository.AutoCmpRepo;
import com.example.patrol_be.debug.entity.AutoCmp;
import java.util.List;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AutoCmpService {
    private final AutoCmpRepo repo;
    public List<AutoCmp> search(String lang, String keyword) {
        return repo.findTop5ByLangAndNoteAndInputTextContainingOrderBySortOrderAsc(lang,"Content",keyword);
    }
    public List<AutoCmp> searchCounter(String lang, String keyword) {
        return repo.findTop5ByLangAndNoteAndInputTextContainingOrderBySortOrderAsc(lang,"Countermeasure",keyword);
    }
    public List<AutoCmp> getAllComment(String lang) {
        return repo.findAllByLangAndNoteOrderBySortOrderAsc(lang,"Content");
    }
    public List<AutoCmp> getAllCounter(String lang) {
        return repo.findAllByLangAndNoteOrderBySortOrderAsc(lang,"Countermeasure");
    }

    public List<AutoCmp> getAll() {
        return repo.findAllByOrderBySortOrderAsc();
    }
}
