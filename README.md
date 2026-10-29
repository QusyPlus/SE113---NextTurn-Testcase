# NextTurn - AutomationTest

Bộ test tự động (**JUnit 5 + Mockito**) cho luồng xác thực và quản lý nhân sự của backend NextTurn, branch `auth-module` (Java 25, Spring Boot 4.1.1). Gồm 111 test: 101 Unit Test Case thiết kế bằng kỹ thuật hộp trắng cho 7 hàm, cộng 10 test kiểm tra quy tắc mã cơ sở.

## Chạy nhanh

Cần **JDK 25** và mã nguồn **backend NextTurn (branch `auth-module`)** đặt cạnh thư mục này:

```
<thư mục bất kỳ>/
├── backend/
└── nextturn-automation-test/   (repo này)
```

Windows:

```powershell
.\run-tests.ps1
```

macOS / Linux / Git Bash:

```bash
./run-tests.sh
```

Script chép tạm test vào backend, chạy `mvnw test`, lưu báo cáo vào `results/` rồi xóa bản chép, nên backend không bị thay đổi. Backend nằm chỗ khác thì thêm `-BackendDir <đường dẫn>` (Windows) hoặc `--backend <đường dẫn>`.

## Cấu trúc

```
├── run-tests.ps1          script cho Windows
├── run-tests.sh           script cho macOS / Linux / Git Bash
├── src/test/java/com/nextturn/lab3/   8 lớp test
├── docs/HUONG-DAN.md      hướng dẫn chi tiết, chạy bằng IDE, lỗi thường gặp
└── results/               báo cáo sinh ra sau khi chạy (không đưa lên repo)
```

## Kết quả trên commit `6f9118b`

| Lớp test | Hàm được test | Kết quả |
|---|---|---|
| `TenantAuthServiceUtcTest` | `TenantAuthService.authenticateTenant` (U10) | 11/11 Passed |
| `OtpServiceVerifyOtpUtcTest` | `OtpService.verifyOtp` (U12) | 15/15 Passed |
| `OtpServiceSendOtpUtcTest` | `OtpService.generateAndSendOtp` (U13) | 12/12 Passed |
| `DomainWhitelistUtcTest` | `CustomerAuthService.isDomainWhitelisted` (U15) | 12/13 – UTCID11 Failed (DF-AUTH-02) |
| `TenantStaffCreateUtcTest` | `TenantStaffService.createStaff` (U16) | 16/17 – UTCID17 Failed (DF-AUTH-07) |
| `TenantStaffUpdateUtcTest` | `TenantStaffService.updateStaff` (U17) | 22/25 – UTCID10 (DF-AUTH-09), UTCID13 (DF-AUTH-06), UTCID25 (DF-AUTH-07) Failed |
| `TenantStaffDeleteUtcTest` | `TenantStaffService.deleteStaff` (U18) | 7/8 – UTCID07 Failed (DF-AUTH-06) |
| `TenantAuthRequestSrsConformanceTest` | Ràng buộc mã cơ sở của `TenantAuthRequest` (F12-004) | 6/10 – SRS05–SRS08 Failed (DF-AUTH-01) |

10 test Failed là các lỗi của mã nguồn mà bộ test bắt được (5 lỗi, một lỗi có thể làm nhiều test đỏ), không phải lỗi của test. Xem giải thích từng lỗi trong `docs/HUONG-DAN.md`.
