package com.example.patrol_be.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "SPatrol_Role", schema = "dbo")
@Data
public class PatrolRole {

    @Id
    @Column(name = "RoleCode")
    private String roleCode;

    @Column(name = "RoleName")
    private String roleName;

    @Column(name = "IsSuper")
    private Boolean isSuper;

    @Column(name = "Note")
    private String note;
}
