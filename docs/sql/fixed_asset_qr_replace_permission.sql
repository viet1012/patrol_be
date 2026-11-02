-- Fixed Asset "3) Thay QR" permission = FixedAsset.AFTER (column CanAfter).
-- Grant to every role that can already audit Fixed Asset (FixedAsset.CanBefore = 1).
-- Run manually. PK is RoleCode + GroupCode, so these roles already have a
-- FixedAsset row: UPDATE, not INSERT. IsSuper roles have full permission already.

-- Check first
SELECT RoleCode, GroupCode, CanBefore, CanAfter, CanRecheck, CanSummary
FROM dbo.[SPatrol_RolePermission]
WHERE GroupCode = 'FixedAsset'
ORDER BY RoleCode;

UPDATE dbo.[SPatrol_RolePermission]
SET CanAfter = 1
WHERE GroupCode = 'FixedAsset'
    AND CanBefore = 1
    AND ISNULL(CanAfter, 0) = 0;

-- Rollback (only if CanAfter was 0 for all FixedAsset rows before):
-- UPDATE dbo.[SPatrol_RolePermission] SET CanAfter = 0 WHERE GroupCode = 'FixedAsset';
