package com.example.patrol_be.permission.repository;

import com.example.patrol_be.permission.entity.PatrolRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PatrolRoleRepo extends JpaRepository<PatrolRole, String> {

    List<PatrolRole> findByRoleCodeIn(Collection<String> roleCodes);
}
