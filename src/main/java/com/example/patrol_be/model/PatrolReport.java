package com.example.patrol_be.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "F2_Patrol_Report")
@Data
public class PatrolReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String type;
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

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String comment;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String countermeasure;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String checkInfo;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String imageNames;

    private LocalDateTime createdAt = LocalDateTime.now();
}
