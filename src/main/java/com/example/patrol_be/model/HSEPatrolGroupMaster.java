package com.example.patrol_be.model;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "HSE_Patrol_Group_Master")
@Data
public class HSEPatrolGroupMaster {
    @Id
    @Column(name = "No")
    private Long id;

    @Column(name = "Plant")
    private String plant;

    @Column(name = "Grp")
    private String fac;

    @Column(name = "Area")
    private String area;

    @Column(name = "MacID")
    private String macId;
}
