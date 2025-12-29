package com.example.patrol_be.repository;

import com.example.patrol_be.model.PatrolAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatrolAccountRepository extends JpaRepository<PatrolAccount, Long> {

    Optional<PatrolAccount> findByAccount(String account);

    boolean existsByAccount(String account);
}

