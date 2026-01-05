package com.example.patrol_be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PatrolRiskPivotResponseDTO {
    private String plant;
    private String atStatus;
    private long grandTotal;
    private PatrolRiskPivotRowDTO totals; // tổng theo cột I..V
    private List<PatrolRiskPivotRowDTO> rows;
}
