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
    private String type;  // thêm trường này

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

    private List<String> imageNames;  // đổi thành List<String>

    private LocalDateTime createdAt;
    private String pic;
    private LocalDateTime dueDate;

    private List<String> at_imageNames;  // đổi thành List<String>
    private String at_comment;
    private LocalDateTime at_date;       // đổi thành LocalDateTime
    private String at_pic;
    private String at_status;

    private String hse_judge;
    private List<String> hse_imageNames;  // đổi thành List<String>
    private String hse_comment;
    private LocalDateTime hse_date;        // đổi thành LocalDateTime

    private String load_status;           // thêm trường này
}
