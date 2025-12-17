package com.example.patrol_be.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "HSE_Patrol_Team_Master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HsePatrolTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "No")
    private Long no;

    @Column(name = "Plant")
    private String plant;

    @Column(name = "Grp")
    private String grp;

    @Column(name = "PIC1")
    private String pic1;

    @Column(name = "PIC2")
    private String pic2;

    @Column(name = "PIC3")
    private String pic3;

    @Column(name = "PIC4")
    private String pic4;

    @Column(name = "PIC5")
    private String pic5;

    @Column(name = "PIC6")
    private String pic6;

    @Column(name = "PIC7")
    private String pic7;

    @Column(name = "PIC8")
    private String pic8;

    @Column(name = "PIC9")
    private String pic9;

    @Column(name = "PIC10")
    private String pic10;

    @Column(name = "Note")
    private String note;
}
