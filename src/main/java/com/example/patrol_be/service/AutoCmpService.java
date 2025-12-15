package com.example.patrol_be.service;

import com.example.patrol_be.repository.AutoCmpRepo;
import com.example.patrol_be.model.AutoCmp;
import java.util.List;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AutoCmpService {
    private final AutoCmpRepo repo;
    public List<AutoCmp> search(String keyword) {
        return repo.findTop10ByInputTextContainingOrderBySortOrderAsc(keyword);
    }

    public List<AutoCmp> getAll() {
        return repo.findAllByOrderBySortOrderAsc();
    }
}
