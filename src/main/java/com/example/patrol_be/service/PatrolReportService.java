package com.example.patrol_be.service;

import com.example.patrol_be.dto.PatrolReportDTO;
import com.example.patrol_be.repository.PatrolReportRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatrolReportService {

    private final PatrolReportRepo repo;

    public List<PatrolReportDTO> search(
            String division,
            String area,
            String machine,
            String type
    ) {
        return repo.search(
                        normalize(division),
                        normalize(area),
                        normalize(machine),
                        normalize(type)
                ).stream()
                .map(this::mapToDto)
                .toList();
    }

    private PatrolReportDTO mapToDto(Object[] r) {
        return new PatrolReportDTO(
                ((Number) r[0]).intValue(),     // stt
                (String) r[1],                  // grp
                (String) r[2],                  // plant
                (String) r[3],                  // division
                (String) r[4],                  // area
                (String) r[5],                  // machine
                (String) r[6],                  // riskFreq
                (String) r[7],                  // riskProb
                (String) r[8],                  // riskSev
                (String) r[9],                  // riskTotal
                (String) r[10],                 // comment
                (String) r[11],                 // countermeasure
                (String) r[12],                 // checkInfo
                (LocalDateTime) r[13],          // checkInfo
                splitImages((String) r[14])     // imageNames
        );
    }

    private List<String> splitImages(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .toList();
    }

    private String normalize(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
