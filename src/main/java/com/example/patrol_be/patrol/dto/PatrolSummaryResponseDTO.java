package com.example.patrol_be.patrol.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

// response tổng
@Data
@Builder
public class PatrolSummaryResponseDTO {
	private LocalDate fromD;
	private LocalDate toD;
	private String plant;
	private String type;
	private List<PatrolFacSummaryDTO> facs;
}