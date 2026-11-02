-- Fixed Asset audit: Pic = MASTER EmailChecked (LOWER(LTRIM(RTRIM())), blank -> NULL) at audit time.
-- Run manually on F2Database. Not executed by the application.

-- 1. Column (run before deploying the BE that inserts Pic)
ALTER TABLE F2Database.dbo.F2_FIXED_ASSET_AUDIT ADD Pic NVARCHAR(255) NULL;
GO

-- 2. Backfill old rows (Pic IS NULL) from MASTER by MachineCode, only when the
--    machine has exactly 1 distinct non-blank EmailChecked.
--    Scope = same as the app (fixed-asset.div / fixed-asset.factory); remove the
--    scope line to use all of F2_FIXED_ASSET.

-- 2a. Preview
WITH master_pic AS (
    SELECT
        LTRIM(RTRIM(fa.MachineCode)) AS MachineCode,
        MIN(LOWER(LTRIM(RTRIM(fa.EmailChecked)))) AS Pic
    FROM F2Database.dbo.F2_FIXED_ASSET fa
    WHERE (LTRIM(RTRIM(fa.Div)) = 'KVH' OR LTRIM(RTRIM(fa.Factory)) = 'Factory 2')
        AND fa.MachineCode IS NOT NULL
        AND LTRIM(RTRIM(fa.MachineCode)) <> ''
        AND fa.EmailChecked IS NOT NULL
        AND LTRIM(RTRIM(fa.EmailChecked)) <> ''
    GROUP BY LTRIM(RTRIM(fa.MachineCode))
    HAVING COUNT(DISTINCT LOWER(LTRIM(RTRIM(fa.EmailChecked)))) = 1
)
SELECT a.Id, a.MachineCode, a.UpdatedAt, a.Pic AS OldPic, mp.Pic AS NewPic
FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT a
INNER JOIN master_pic mp
    ON mp.MachineCode = LTRIM(RTRIM(a.MachineCode))
WHERE a.Pic IS NULL
ORDER BY a.UpdatedAt DESC, a.Id DESC;

-- 2b. Update (check @@ROWCOUNT = preview row count, then COMMIT; otherwise ROLLBACK)
BEGIN TRANSACTION;

WITH master_pic AS (
    SELECT
        LTRIM(RTRIM(fa.MachineCode)) AS MachineCode,
        MIN(LOWER(LTRIM(RTRIM(fa.EmailChecked)))) AS Pic
    FROM F2Database.dbo.F2_FIXED_ASSET fa
    WHERE (LTRIM(RTRIM(fa.Div)) = 'KVH' OR LTRIM(RTRIM(fa.Factory)) = 'Factory 2')
        AND fa.MachineCode IS NOT NULL
        AND LTRIM(RTRIM(fa.MachineCode)) <> ''
        AND fa.EmailChecked IS NOT NULL
        AND LTRIM(RTRIM(fa.EmailChecked)) <> ''
    GROUP BY LTRIM(RTRIM(fa.MachineCode))
    HAVING COUNT(DISTINCT LOWER(LTRIM(RTRIM(fa.EmailChecked)))) = 1
)
UPDATE a
SET a.Pic = mp.Pic
FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT a
INNER JOIN master_pic mp
    ON mp.MachineCode = LTRIM(RTRIM(a.MachineCode))
WHERE a.Pic IS NULL;

SELECT @@ROWCOUNT AS UpdatedRows;

-- COMMIT TRANSACTION;
-- ROLLBACK TRANSACTION;

-- Rows still NULL after backfill (no MASTER / no or ambiguous EmailChecked)
-- SELECT Id, MachineCode, UpdatedAt FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT WHERE Pic IS NULL;
