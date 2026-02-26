package com.example.patrol_be.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// 1 section = 1 FAC (Fac_A / Fac_B / Fac_C)
@Data
@Builder
public class PatrolFacSummaryDTO {
	private String fac;

	// ✅ rows phải là list của từng PIC row
	private List<PatrolPicRowDTO> rows;

	// ✅ total cũng là 1 row kiểu PIC
	private PatrolPicRowDTO total;

	private Double finishedRate;
	private Double remainRate;

	private Double okRate;
	private Double ngRate;
}