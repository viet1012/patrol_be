package com.example.patrol_be.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "HSE_Patrol_Account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatrolAccount {


    @Id
    @Column(name = "Account", unique = true, nullable = false)
    private String account;

    @Column(name = "Pass", nullable = false)
    private String pass;
}
