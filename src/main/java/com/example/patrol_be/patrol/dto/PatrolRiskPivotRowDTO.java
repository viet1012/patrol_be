package com.example.patrol_be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatrolRiskPivotRowDTO {
    private String pic;   // row label
    private long i;
    private long ii;
    private long iii;
    private long iv;
    private long v;
    private long total;   // grand total for this pic
}
