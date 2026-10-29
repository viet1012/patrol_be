package com.example.patrol_be.patrol.dto;

import lombok.Data;

@Data
public class UpdateReportImageDTO {
    private Long reportId;
    private String oldImage;
    private String newImage;
}
