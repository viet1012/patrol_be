package com.example.patrol_be.dto;



import lombok.Data;
import java.util.List;

@Data
public class ReportRequest {
    private int stt;
    private String group;

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

    private String check;

    // Danh sách tên file ảnh đã lưu (sẽ được set từ service)
    private List<String> imageFileNames;
}