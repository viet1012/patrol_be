package com.example.patrol_be.dto;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PatrolReportDTO {
    private Integer id;
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
    private LocalDateTime dueDate;

    private List<String> imageNames;

    /// PATROL_AFTER
    private String at_imageNames;
    private String at_comment;
    private LocalDate at_date;
    private String at_pic;
    private String at_status;

    /// HSE_CHECK
    private String hse_judge;
    private String hse_imageNames;
    private String hse_comment;
    private LocalDate hse_date;
}
