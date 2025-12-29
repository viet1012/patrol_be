package com.example.patrol_be.dto;



import jakarta.persistence.Column;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
public class ReportRequest {
    private int stt;
    private String group;
    private String type;

    private String plant;
    private String division;
    private String area;
    private String machine;


    private String riskFreq;
    private String riskProb;
    private String riskSev;
    private String riskTotal;

    private String pic;
    private Date dueDate;

    private String comment;
    private String countermeasure;

    private String check;

    // Danh sách tên file ảnh đã lưu (sẽ được set từ service)
    private List<String> imageFileNames;
    private String userCreate;

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