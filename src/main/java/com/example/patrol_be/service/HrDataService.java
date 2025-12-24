package com.example.patrol_be.service;

import com.example.patrol_be.repository.HrDataRepository;
import org.springframework.stereotype.Service;

@Service
public class HrDataService {
    private final HrDataRepository hrDataRepository;

    public HrDataService(HrDataRepository hrDataRepository) {
        this.hrDataRepository = hrDataRepository;
    }

    public String getNameByCode(String code) {
        return hrDataRepository.findNameByCode(code);
    }
}
