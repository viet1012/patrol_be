package com.example.patrol_be.permission.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "SPatrol_RolePermission", schema = "dbo")
@IdClass(PatrolRolePermission.Pk.class)
@Data
public class PatrolRolePermission {

    @Id
    @Column(name = "RoleCode")
    private String roleCode;

    @Id
    @Column(name = "GroupCode")
    private String groupCode;

    @Column(name = "CanBefore")
    private Boolean canBefore;

    @Column(name = "CanAfter")
    private Boolean canAfter;

    @Column(name = "CanRecheck")
    private Boolean canRecheck;

    @Column(name = "CanSummary")
    private Boolean canSummary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private String roleCode;
        private String groupCode;
    }
}
