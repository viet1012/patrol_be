package com.example.patrol_be.dto;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PatrolReportDTO {

    private Integer stt;
    private String grp;
    private String plant;
    private String division;
    private String area;
    private String machine;

    private String riskFreq;
    private String riskProb;
    private String riskSev;
    private String riskTotal;

    private String comment;
    private String countermeasure;
    private String checkInfo;

    private List<String> imageNames;
}
