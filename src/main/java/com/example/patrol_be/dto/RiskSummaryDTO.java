package com.example.patrol_be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskSummaryDTO {

    private String grp;
    private String division;

    private Integer minus;
    private Integer i;
    private Integer ii;
    private Integer iii;
    private Integer iv;
    private Integer v;
}
