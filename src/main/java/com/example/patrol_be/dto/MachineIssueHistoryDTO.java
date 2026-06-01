package com.example.patrol_be.dto;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MachineIssueHistoryDTO {

	private String fac;
	private String division;
	private String area;
	private String machine;

	private Integer totalCases;

	private List<String> comments;
}