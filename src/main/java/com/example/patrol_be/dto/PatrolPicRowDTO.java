package com.example.patrol_be.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PatrolPicRowDTO {
	private String pic;   // tên PIC hoặc "TOTAL"

	// BEFORE (NG points) = REDO
	private RiskBreakdownDTO before;

	// AFTER TOTAL (Pro action) => Finished + Remain
	private RiskBreakdownDTO finished;
	private RiskBreakdownDTO remain;

	// HSE re-check => OK + NG + TotalAll
	private long recheckAllTotal;
	private RiskBreakdownDTO recheckOk;
	private RiskBreakdownDTO recheckNg;
}