# Phân quyền (Role × Group × Action)

Ma trận quyền nằm trong DB (SQL Server), ở 2 bảng:

- `dbo.[SPatrol_Role]` (`RoleCode` PK, `RoleName`, `IsSuper` bit, `Note`): danh sách role hợp lệ.
- `dbo.[SPatrol_RolePermission]` (`RoleCode`, `GroupCode`, `CanBefore`, `CanAfter`, `CanRecheck`, `CanSummary`; PK `RoleCode + GroupCode`): quyền của từng role trên từng group.

Quyền chỉ được tính ở một chỗ duy nhất: `PermissionService` (`src/main/java/com/example/patrol_be/permission/`).
Service đọc DB **mỗi lần gọi** (không cache), nên sửa DB là có hiệu lực ngay, không cần restart BE.

## Quy tắc tính

- **Role** lấy từ cột `HSE_EmpID.Role`. Một người có thể có nhiều role, cách nhau bằng dấu phẩy (ví dụ `QA,MA`). Mỗi role được trim và chuyển chữ hoa.
- **Role không có trong `SPatrol_Role`** bị bỏ qua, và BE ghi `log.warn` (`Unknown role ...`) kèm giá trị gốc của cột Role.
- **`USER` là quyền cơ bản**, luôn áp cho mọi người, kể cả khi cột Role trống hoặc toàn role không hợp lệ.
- **Quyền cuối** = quyền của `USER` OR quyền của từng role hợp lệ.
- **`IsSuper = 1`**: nếu user có một role như vậy, user có full quyền ở mọi group và mọi action. Các dòng trong `SPatrol_RolePermission` của user đó không cần xét.
- **`GroupCode` không khớp enum `PatrolGroup`** (so sánh không phân biệt hoa thường): dòng đó bị bỏ qua và BE ghi `log.warn` (`Unknown GroupCode ...`).
- Group không có dòng nào cho các role của user thì mọi action ở group đó là `false`.
- Không còn cơ chế deny. Muốn bớt quyền thì sửa hoặc xoá dòng tương ứng trong `SPatrol_RolePermission`.

**Group**: `Patrol`, `Audit`, `QualityPatrol`, `AssetUpdate`, `FixedAsset`. Tên phải khớp enum `PatrolGroup` ở cả BE và FE.
**Action**: `BEFORE`, `AFTER`, `RECHECK`, `SUMMARY`, tương ứng các cột `CanBefore`, `CanAfter`, `CanRecheck`, `CanSummary`. Trong JSON, key là chữ thường (`before`…).
Ở group `FixedAsset`: BEFORE = kiểm kê, RECHECK = in lại QR.

## API

`GET /api/hr/me?code=...`:

```json
{
  "empId": "...", "empName": "...", "plant": "...", "role": "QA,MA",
  "permissions": {
    "Patrol":        { "before": true,  "after": true,  "recheck": false, "summary": true },
    "Audit":         { "before": false, "after": false, "recheck": false, "summary": false },
    "...": {}
  }
}
```

- `role`: các role hợp lệ của user, nối bằng dấu phẩy. Nếu user không có role hợp lệ nào thì trả `USER`.
- `permissions`: luôn đủ mọi group và mọi action.

`GET /api/hr/recheck?code=...&group=...` trả 403 nếu `permissions[group].recheck` là `false`.

Ma trận hiện tại xem trực tiếp trong DB:

```sql
SELECT r.RoleCode, r.IsSuper, p.GroupCode, p.CanBefore, p.CanAfter, p.CanRecheck, p.CanSummary
FROM dbo.[SPatrol_Role] r
LEFT JOIN dbo.[SPatrol_RolePermission] p ON p.RoleCode = r.RoleCode
ORDER BY r.RoleCode, p.GroupCode;
```

## Checklist: thêm role mới

1. Thêm role vào `SPatrol_Role`:
   ```sql
   INSERT INTO dbo.[SPatrol_Role] (RoleCode, RoleName, IsSuper, Note)
   VALUES ('NEWROLE', N'Tên role', 0, N'Ghi chú');
   ```
   Nếu role có toàn quyền, đặt `IsSuper = 1` và bỏ qua bước 2.
2. Thêm quyền vào `SPatrol_RolePermission`. Chỉ cần khai báo phần **cộng thêm** so với `USER`:
   ```sql
   INSERT INTO dbo.[SPatrol_RolePermission] (RoleCode, GroupCode, CanBefore, CanAfter, CanRecheck, CanSummary)
   VALUES ('NEWROLE', 'Audit', 0, 0, 0, 1);
   ```
3. Gán role cho nhân viên trong cột `HSE_EmpID.Role`. Dùng đúng `RoleCode`; nhiều role thì cách nhau bằng dấu phẩy (ví dụ `QA,NEWROLE`).
4. Gọi `/api/hr/me?code=<mã NV>` để kiểm tra kết quả. Xem log có dòng `Unknown role` hoặc `Unknown GroupCode` hay không.

## Checklist: thêm group mới

1. **DB**: cập nhật CHECK constraint của cột `SPatrol_RolePermission.GroupCode` để chấp nhận tên group mới.
2. **BE**: thêm hằng vào enum `PatrolGroup` (`permission/PatrolGroup.java`). Tên enum chính là key JSON và phải trùng `GroupCode` trong DB.
3. **DB**: insert quyền cho group mới vào `SPatrol_RolePermission`, cho `USER` và/hoặc các role cần quyền. Role `IsSuper = 1` tự động có full quyền.
4. **FE**: thêm đúng tên đó vào `enum PatrolGroup` (`lib/core/models/patrol_group.dart`). Tên phải trùng hoàn toàn, kể cả hoa thường.
5. **FE**: cấu hình UI cho group mới, gồm các `switch (PatrolGroup)` trong `lib/features/home/patrol_home_screen.dart` (menu, tiêu đề, màn hình mở ra) và các màn hình dùng `auth.can(group, action)`.

Thứ tự triển khai: DB (bước 1, 3) và BE (bước 2) trước, FE sau. Nếu insert `GroupCode` mới khi BE chưa có enum, BE chỉ ghi `log.warn` và bỏ qua dòng đó; app không lỗi.

> Lưu ý: hiện chưa có API ghi dữ liệu nào ở BE enforce ma trận này. Ma trận chỉ được trả cho FE qua `/api/hr/me` và dùng ở `/api/hr/recheck`.
