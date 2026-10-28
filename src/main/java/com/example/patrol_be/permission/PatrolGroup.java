package com.example.patrol_be.permission;

/**
 * Nhóm workflow. Tên enum = key trong permissions của /api/hr/me,
 * phải khớp PatrolGroup bên FE (lib/core/models/patrol_group.dart).
 */
public enum PatrolGroup {
    Patrol,
    Audit,
    QualityPatrol,
    AssetUpdate,
    FixedAsset
}
