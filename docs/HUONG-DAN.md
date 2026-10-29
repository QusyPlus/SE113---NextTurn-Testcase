# Hướng dẫn chi tiết

## 1. Bộ test này làm gì?

Mỗi hàm được kiểm thử có một bảng Unit Test Case (UTCID01, UTCID02, …) thiết kế bằng kỹ thuật hộp trắng (CFG, độ phức tạp V(G), tập đường độc lập) kết hợp phân hoạch tương đương và giá trị biên. Mỗi UTC được viết thành đúng một phương thức `@Test` để máy tự chạy và so kết quả.

Backend NextTurn viết bằng Java nên bộ test dùng **JUnit 5**, công cụ tương đương NUnit trong Java, cộng **Mockito** để giả lập những thứ bên ngoài như database, Valkey, Keycloak, email. Nhờ vậy test chạy được mà không cần bật server hay database.

| NUnit (C#) | JUnit 5 (NextTurn) |
|---|---|
| `[TestFixture]` class | class `...Test` |
| `[Test]` | `@Test` |
| `Assert.AreEqual(a, b)` | `assertEquals(a, b)` |
| Test Explorer trong Visual Studio | Testing trong VS Code / IntelliJ, hoặc `mvnw test` |

## 2. Các file

| File | Công dụng |
|---|---|
| `run-tests.ps1` | Script chạy test trên Windows (PowerShell). |
| `run-tests.sh` | Script chạy test trên macOS, Linux hoặc Git Bash. |
| `src/test/java/com/nextturn/lab3/TenantAuthServiceUtcTest.java` | 11 test cho `TenantAuthService.authenticateTenant` (nhập mã cơ sở) – U10. |
| `.../OtpServiceVerifyOtpUtcTest.java` | 15 test cho `OtpService.verifyOtp` (mã OTP đúng/sai/hết hạn/sai quá 3 lần) – U12. |
| `.../OtpServiceSendOtpUtcTest.java` | 12 test cho `OtpService.generateAndSendOtp` (chờ 60 giây, tối đa 5 lần/ngày) – U13. |
| `.../DomainWhitelistUtcTest.java` | 13 test cho `CustomerAuthService.isDomainWhitelisted` (tên miền được nhúng widget) – U15. |
| `.../TenantStaffCreateUtcTest.java` | 17 test cho `TenantStaffService.createStaff` (tạo nhân sự và tài khoản Keycloak) – U16. |
| `.../TenantStaffUpdateUtcTest.java` | 25 test cho `TenantStaffService.updateStaff` (đổi thông tin, vai trò, trạng thái, đồng bộ Keycloak) – U17. |
| `.../TenantStaffDeleteUtcTest.java` | 8 test cho `TenantStaffService.deleteStaff` (xóa mềm nhân sự, xóa tài khoản Keycloak) – U18. |
| `.../TenantAuthRequestSrsConformanceTest.java` | 10 test kiểm tra quy tắc mã cơ sở (3–30 ký tự, `a-z 0-9 -`, ca F12-004 hộp đen). |
| `results/` | Sinh ra sau khi chạy (không đưa lên repo). `*.txt`: tóm tắt từng lớp và lý do test Failed; `TEST-*.xml`: cùng kết quả dạng XML. |

Tên phương thức bắt đầu bằng mã UTC. Ví dụ `utcid11_tenMienGiaMaoCoDuoiTrungEvilpkalpha` trong `DomainWhitelistUtcTest` là UTCID11 của U15.

## 3. Chuẩn bị

1. **JDK 25.** Backend dùng Java 25, JDK 21 trở xuống không biên dịch được. Kiểm tra bằng `java -version`.
   - Windows: nếu đã có JDK 25 trong `%USERPROFILE%\.jdks` (IntelliJ hoặc VS Code tải về) thì script tự dùng. Nếu không, truyền `-JavaHome <thư mục JDK 25>`.
   - macOS / Linux: đặt `JAVA_HOME` trỏ tới JDK 25 trước khi chạy.
2. **Repo backend NextTurn, branch `auth-module`.** Các test này gọi thẳng mã của backend nên cần có mã nguồn backend trên máy.
3. **Không cần cài Maven:** backend có sẵn `mvnw`. Lần chạy đầu sẽ tải thư viện nên mất vài phút.

Cách đặt thư mục được khuyên dùng (script tự tìm thư mục `backend` nằm cạnh):

```
<thư mục bất kỳ>/
├── backend/                     repo backend, branch auth-module
└── nextturn-automation-test/    repo này
```

Nếu backend nằm chỗ khác, chỉ đường dẫn bằng `-BackendDir` (Windows), `--backend` (macOS/Linux) hoặc biến môi trường `NEXTTURN_BACKEND`.

## 4. Cách 1: chạy bằng script

Windows (PowerShell, tại thư mục repo này):

```powershell
.\run-tests.ps1
```

Nếu PowerShell chặn chạy script: `powershell -ExecutionPolicy Bypass -File .\run-tests.ps1`.

macOS / Linux / Git Bash:

```bash
chmod +x run-tests.sh   # chỉ cần một lần
./run-tests.sh
```

Script chép tạm các lớp test vào `backend/src/test/java/com/nextturn/lab3`, chạy `mvnw test -Dtest=com.nextturn.lab3.*Test`, lấy báo cáo về `results/` rồi xóa bản chép, nên repo backend không bị thay đổi. Màn hình in các dòng `[ERROR] ...` cho test bị Failed (kèm lý do), rồi một dòng tổng và 8 dòng tóm tắt:

```
[ERROR] Tests run: 111, Failures: 10, Errors: 0, Skipped: 0
Tests run: 13, Failures: 1 ... DomainWhitelistUtcTest
Tests run: 12, Failures: 0 ... OtpServiceSendOtpUtcTest
Tests run: 15, Failures: 0 ... OtpServiceVerifyOtpUtcTest
Tests run: 10, Failures: 4 ... TenantAuthRequestSrsConformanceTest
Tests run: 11, Failures: 0 ... TenantAuthServiceUtcTest
Tests run: 17, Failures: 1 ... TenantStaffCreateUtcTest
Tests run: 8, Failures: 1 ... TenantStaffDeleteUtcTest
Tests run: 25, Failures: 3 ... TenantStaffUpdateUtcTest
```

Chữ `[ERROR]` ở đây chỉ có nghĩa là có test Failed, script không bị hỏng.

## 5. Cách 2: chạy trong IDE (giống Test Explorer của NUnit)

1. Chép test vào backend: `.\run-tests.ps1 -CopyOnly` (hoặc `./run-tests.sh --copy-only`).
2. Mở thư mục `backend` bằng VS Code (cài Extension Pack for Java) hoặc IntelliJ IDEA, chờ IDE tải xong dự án Maven.
3. VS Code: bấm biểu tượng **bình thí nghiệm (Testing)** ở thanh bên trái, mở `com.nextturn.lab3`. IntelliJ: chuột phải thư mục `lab3` → Run Tests.
4. Test đạt có dấu tick xanh, test Failed có dấu X đỏ. Bấm vào test đỏ để xem dòng `expected: ... but was: ...`.
5. Xong thì xóa bản chép để repo backend sạch: `.\run-tests.ps1 -Remove` (hoặc `./run-tests.sh --remove`).

Nếu VS Code báo lỗi phiên bản Java, mở Settings, tìm `java.configuration.runtimes` và thêm đường dẫn JDK 25.

## 6. Cách 3: lệnh Maven thủ công

Chép thư mục `src/test/java/com/nextturn/lab3` vào `backend/src/test/java/com/nextturn/`, rồi tại thư mục backend:

```bash
./mvnw test "-Dtest=com.nextturn.lab3.*Test"
```

(Windows: `.\mvnw.cmd test "-Dtest=com.nextturn.lab3.*Test"`.) Báo cáo nằm ở `backend/target/surefire-reports`. Nhớ xóa thư mục `lab3` đã chép sau khi chạy.

## 7. Kết quả mong đợi (commit `6f9118b`)

| Lớp test | Kết quả | Ý nghĩa |
|---|---|---|
| TenantAuthServiceUtcTest | 11/11 Passed | Nhập mã cơ sở hoạt động đúng |
| OtpServiceVerifyOtpUtcTest | 15/15 Passed | Kiểm tra OTP đúng |
| OtpServiceSendOtpUtcTest | 12/12 Passed | Giới hạn gửi OTP đúng |
| DomainWhitelistUtcTest | 12/13, **UTCID11 Failed** | DF-AUTH-02: `evilpkalpha.vn` lọt qua `*.pkalpha.vn` |
| TenantAuthRequestSrsConformanceTest | 6/10, **SRS05–SRS08 Failed** | DF-AUTH-01: mã cơ sở chấp nhận "ab", 31 ký tự, chữ hoa, dấu `_` |
| TenantStaffCreateUtcTest | 16/17, **UTCID17 Failed** | DF-AUTH-07: thêm vào organization lỗi thì tài khoản Keycloak vừa tạo bị bỏ lại |
| TenantStaffUpdateUtcTest | 22/25, **UTCID10, 13, 25 Failed** | DF-AUTH-09: đổi COUNTER ↔ ADMIN không cập nhật vai trò trên Keycloak; DF-AUTH-06: hạ quyền mà xóa Keycloak lỗi vẫn báo thành công; DF-AUTH-07 |
| TenantStaffDeleteUtcTest | 7/8, **UTCID07 Failed** | DF-AUTH-06: xóa nhân sự mà xóa Keycloak lỗi thì người đó vẫn đăng nhập được |

Tổng cộng: **111 test, 10 Failed**. **Test Failed ở đây là kết quả đúng của việc kiểm thử:** test viết theo hành vi mong đợi, còn mã nguồn đang sai, nên test đỏ là đã bắt được lỗi. Một lỗi có thể làm nhiều test cùng đỏ (DF-AUTH-01 làm 4 test SRS05–SRS08 Failed), nên 10 test Failed ứng với 5 lỗi.

- Khi nhóm phát triển sửa xong DF-AUTH-01, 02, 06, 07, 09: kỳ vọng là **111/111 Passed**.
- Nếu kết quả khác bảng trên trên cùng commit, hãy kiểm tra lại JDK và branch của backend.
- Nếu một test đang xanh bỗng thành đỏ sau khi backend có commit mới, code đã thay đổi hành vi (lỗi hồi quy).
- Nếu không biên dịch được (`COMPILATION ERROR`), tên lớp, hàm hoặc tham số trong backend đã đổi; cần cập nhật test cho khớp.

## 8. Lỗi thường gặp

| Thông báo | Cách xử lý |
|---|---|
| `Không tìm thấy thư mục backend` | Đặt repo backend cạnh repo này, hoặc chạy với `-BackendDir` / `--backend`. |
| `Maven không tạo báo cáo test` hoặc `release version 25 not supported` | Máy đang dùng JDK cũ hơn 25. Cài JDK 25 rồi truyền `-JavaHome` hoặc đặt `JAVA_HOME`. |
| `Thư mục .../lab3 đã tồn tại` | Lần trước đã chạy `-CopyOnly` / `--copy-only`. Chạy `-Remove` / `--remove` rồi chạy lại. |
| `running scripts is disabled on this system` | Chạy `powershell -ExecutionPolicy Bypass -File .\run-tests.ps1`. |
| `Permission denied` khi chạy `./run-tests.sh` | Chạy `chmod +x run-tests.sh` hoặc `bash run-tests.sh`. |
