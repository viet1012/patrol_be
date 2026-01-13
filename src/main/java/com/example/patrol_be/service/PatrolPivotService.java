package com.example.patrol_be.service;

import com.example.patrol_be.dto.PatrolRiskPivotResponseDTO;
import com.example.patrol_be.dto.PatrolRiskPivotRowDTO;
import com.example.patrol_be.repository.PatrolReportRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatrolPivotService {

    private final PatrolReportRepo repo;

    public PatrolRiskPivotResponseDTO getPivot(String plant, List<String> atStatuses) {
        List<Object[]> raw = repo.pivotByPicAndRisk(plant, atStatuses);

        List<PatrolRiskPivotRowDTO> rows = new ArrayList<>();
        long sumI = 0, sumII = 0, sumIII = 0, sumIV = 0, sumV = 0, grand = 0;

        for (Object[] r : raw) {
            String pic = (String) r[0];
            long i   = ((Number) r[1]).longValue();
            long ii  = ((Number) r[2]).longValue();
            long iii = ((Number) r[3]).longValue();
            long iv  = ((Number) r[4]).longValue();
            long v   = ((Number) r[5]).longValue();
            long total = ((Number) r[6]).longValue();

            rows.add(new PatrolRiskPivotRowDTO(pic, i, ii, iii, iv, v, total));

            sumI += i; sumII += ii; sumIII += iii; sumIV += iv; sumV += v; grand += total;
        }

        var totals = new PatrolRiskPivotRowDTO("Grand Total", sumI, sumII, sumIII, sumIV, sumV, grand);

        // join lại để trả response (nếu bạn muốn hiển thị)
        String statusText = String.join(",", atStatuses);

        return new PatrolRiskPivotResponseDTO(plant, statusText, grand, totals, rows);
    }

}

