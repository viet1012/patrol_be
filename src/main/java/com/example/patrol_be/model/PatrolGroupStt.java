package com.example.patrol_be.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "\"F2_PatrolGroupStt\"")
@Data

public class PatrolGroupStt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @Column(name = "workDate")
//    private LocalDate workDate;

    @Column(nullable = false)
    private String fac;

//    @Column(nullable = false)
//    private String grp;

    @Column(nullable = false)
    private int currentStt;
}
