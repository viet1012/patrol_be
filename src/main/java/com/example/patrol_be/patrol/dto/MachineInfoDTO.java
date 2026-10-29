package com.example.patrol_be.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MachineInfoDTO {
	private String plant;
	private String fac;
	private String area;
	private String macId;
	private String pic;
}