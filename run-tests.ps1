# Chạy bộ test tự động (JUnit 5) trên repo backend NextTurn, branch auth-module.
# Mặc định: chép tạm các lớp test vào <backend>/src/test/java/com/nextturn/lab3, chạy mvnw,
# lấy báo cáo Surefire về thư mục results\ rồi xóa các lớp đã chép (repo backend không bị thay đổi).
#
#   .\run-tests.ps1                         chạy toàn bộ test
#   .\run-tests.ps1 -BackendDir D:\backend  chỉ rõ thư mục backend
#   .\run-tests.ps1 -CopyOnly               chỉ chép test vào backend để chạy bằng IDE (VS Code, IntelliJ)
#   .\run-tests.ps1 -Remove                 xóa thư mục test đã chép bằng -CopyOnly
#
# Thư mục backend được tìm theo thứ tự: -BackendDir, biến môi trường NEXTTURN_BACKEND,
# rồi thư mục "backend" nằm cạnh thư mục này hoặc ở các thư mục cha.
# Yêu cầu: JDK 25 (truyền -JavaHome nếu JAVA_HOME trên máy đang trỏ tới JDK khác).
param(
    [string]$BackendDir = $env:NEXTTURN_BACKEND,
    [string]$JavaHome = '',
    [switch]$CopyOnly,
    [switch]$Remove
)
$ErrorActionPreference = 'Stop'

if (-not $BackendDir) {
    $dir = $PSScriptRoot
    while ($dir -and -not (Test-Path (Join-Path $dir 'backend\mvnw.cmd'))) { $dir = Split-Path $dir -Parent }
    if (-not $dir) { throw 'Không tìm thấy thư mục backend. Đặt repo backend cạnh thư mục này, hoặc chạy với -BackendDir <đường dẫn>.' }
    $BackendDir = Join-Path $dir 'backend'
}
$backend = (Resolve-Path $BackendDir).Path
if (-not (Test-Path (Join-Path $backend 'mvnw.cmd'))) { throw "$backend không phải thư mục backend (không có mvnw.cmd)." }
$target = Join-Path $backend 'src\test\java\com\nextturn\lab3'
$reports = Join-Path $backend 'target\surefire-reports'
$results = Join-Path $PSScriptRoot 'results'
"Backend: $backend"

if ($Remove) {
    if (Test-Path $target) { Remove-Item -Recurse -Force $target; "Đã xóa $target" } else { "Không có $target" }
    return
}
if (Test-Path $target) { throw "Thư mục $target đã tồn tại (có thể do -CopyOnly), chạy lại với -Remove trước." }
Copy-Item (Join-Path $PSScriptRoot 'src\test\java\com\nextturn\lab3') $target -Recurse
if ($CopyOnly) {
    "Đã chép test vào $target. Mở thư mục backend bằng IDE để chạy; xong thì chạy lại script với -Remove."
    return
}

function Get-JdkMajor([string]$home_) {
    $release = Join-Path $home_ 'release'
    if (-not (Test-Path $release)) { return 0 }
    $m = Select-String -Path $release -Pattern 'JAVA_VERSION="(\d+)' | Select-Object -First 1
    if ($m) { [int]$m.Matches[0].Groups[1].Value } else { 0 }
}
if (-not $JavaHome -and (-not $env:JAVA_HOME -or (Get-JdkMajor $env:JAVA_HOME) -lt 25)) {
    # JAVA_HOME chưa trỏ tới JDK 25: thử JDK do IntelliJ / VS Code tải về trong %USERPROFILE%\.jdks
    $jdk = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
        Where-Object { (Get-JdkMajor $_.FullName) -ge 25 } | Select-Object -First 1
    if ($jdk) { $JavaHome = $jdk.FullName }
}
if ($JavaHome) { $env:JAVA_HOME = $JavaHome; $env:Path = "$JavaHome\bin;$env:Path" }
"JDK: $env:JAVA_HOME"
try {
    # Xóa báo cáo của lần chạy trước để không lấy nhầm kết quả cũ khi Maven lỗi.
    Remove-Item (Join-Path $reports '*com.nextturn.lab3.*') -ErrorAction SilentlyContinue
    # Gọi qua cmd để cảnh báo Maven ghi ra stderr không bị Windows PowerShell 5 coi là lỗi;
    # ".\" để chạy được cả khi máy bật NoDefaultCurrentDirectoryInExePath.
    cmd /c "cd /d `"$backend`" && .\mvnw.cmd -q test -Dtest=com.nextturn.lab3.*Test -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=true 2>&1" |
        Select-String -Pattern 'Tests run:|ERROR|BUILD FAILURE' | ForEach-Object { $_.Line }
    if (-not (Test-Path (Join-Path $reports '*com.nextturn.lab3.*'))) { throw 'Maven không tạo báo cáo test, xem lỗi ở trên (thường do chưa cài JDK 25).' }
    Remove-Item -Recurse -Force $results -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $results | Out-Null
    Copy-Item (Join-Path $reports '*com.nextturn.lab3.*') $results
}
finally {
    Remove-Item -Recurse -Force $target
}
''
'Tóm tắt (báo cáo chi tiết trong thư mục results):'
Get-ChildItem $results -Filter *.txt | Get-Content | Select-String 'Tests run'
