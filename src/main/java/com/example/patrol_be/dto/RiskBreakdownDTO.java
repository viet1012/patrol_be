package com.example.patrol_be.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

// Risk breakdown: Total + I..V (có cả "-" nếu cần)
@Data
@Builder
public class RiskBreakdownDTO {
	private long total;
	private long i;
	private long ii;
	private long iii;
	private long iv;
	private long v;
}


