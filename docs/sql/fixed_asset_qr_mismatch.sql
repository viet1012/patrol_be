-- Fixed Asset audit: QR location recorded in Manual mode.
-- Run manually on F2Database. Not executed by the application.

ALTER TABLE dbo.F2_FIXED_ASSET_AUDIT ADD
    Floor_Qr   NVARCHAR(50) NULL,
    A_Qr       NVARCHAR(50) NULL,
    AA_Qr      NVARCHAR(50) NULL,
    QrMismatch BIT NOT NULL CONSTRAINT DF_F2_FIXED_ASSET_AUDIT_QrMismatch DEFAULT 0;
