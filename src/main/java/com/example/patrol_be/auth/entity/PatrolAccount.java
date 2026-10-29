package com.example.patrol_be.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "NewDT")
    private LocalDateTime newDT;

    @Column(name = "UpdDT")
    private LocalDateTime updDT;

    @Column(name = "Last_Login")
    private LocalDateTime lastLogin;

}
