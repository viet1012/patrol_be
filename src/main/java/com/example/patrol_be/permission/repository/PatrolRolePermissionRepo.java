package com.example.patrol_be.permission.repository;

import com.example.patrol_be.permission.entity.PatrolRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PatrolRolePermissionRepo extends JpaRepository<PatrolRolePermission, PatrolRolePermission.Pk> {

    List<PatrolRolePermission> findByRoleCodeIn(Collection<String> roleCodes);
}
