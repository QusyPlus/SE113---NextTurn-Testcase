#!/usr/bin/env bash
# Chạy bộ test tự động (JUnit 5) trên repo backend NextTurn, branch auth-module (macOS, Linux, Git Bash).
# Mặc định: chép tạm các lớp test vào <backend>/src/test/java/com/nextturn/lab3, chạy mvnw,
# lấy báo cáo Surefire về thư mục results/ rồi xóa các lớp đã chép (repo backend không bị thay đổi).
#
#   ./run-tests.sh                      chạy toàn bộ test
#   ./run-tests.sh --backend ~/backend  chỉ rõ thư mục backend
#   ./run-tests.sh --copy-only          chỉ chép test vào backend để chạy bằng IDE
#   ./run-tests.sh --remove             xóa thư mục test đã chép bằng --copy-only
#
# Thư mục backend được tìm theo thứ tự: --backend, biến môi trường NEXTTURN_BACKEND,
# rồi thư mục "backend" nằm cạnh thư mục này hoặc ở các thư mục cha. Yêu cầu: JDK 25.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND="${NEXTTURN_BACKEND:-}"
MODE=run

while [ $# -gt 0 ]; do
  case "$1" in
    --backend) BACKEND="$2"; shift 2 ;;
    --copy-only) MODE=copy; shift ;;
    --remove) MODE=remove; shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "Tham số không hợp lệ: $1" >&2; exit 2 ;;
  esac
done

if [ -z "$BACKEND" ]; then
  dir="$SCRIPT_DIR"
  while [ "$dir" != "/" ] && [ ! -f "$dir/backend/mvnw" ]; do dir="$(dirname "$dir")"; done
  if [ ! -f "$dir/backend/mvnw" ]; then
    echo "Không tìm thấy thư mục backend. Đặt repo backend cạnh thư mục này, hoặc chạy với --backend <đường dẫn>." >&2
    exit 1
  fi
  BACKEND="$dir/backend"
fi
BACKEND="$(cd "$BACKEND" && pwd)"
[ -f "$BACKEND/mvnw" ] || { echo "$BACKEND không phải thư mục backend (không có mvnw)." >&2; exit 1; }
TARGET="$BACKEND/src/test/java/com/nextturn/lab3"
REPORTS="$BACKEND/target/surefire-reports"
RESULTS="$SCRIPT_DIR/results"
echo "Backend: $BACKEND"

if [ "$MODE" = remove ]; then
  if [ -d "$TARGET" ]; then rm -rf "$TARGET"; echo "Đã xóa $TARGET"; else echo "Không có $TARGET"; fi
  exit 0
fi
if [ -e "$TARGET" ]; then
  echo "Thư mục $TARGET đã tồn tại (có thể do --copy-only), chạy lại với --remove trước." >&2
  exit 1
fi
cp -R "$SCRIPT_DIR/src/test/java/com/nextturn/lab3" "$TARGET"
if [ "$MODE" = copy ]; then
  echo "Đã chép test vào $TARGET. Mở thư mục backend bằng IDE để chạy; xong thì chạy lại script với --remove."
  exit 0
fi

trap 'rm -rf "$TARGET"' EXIT
# Xóa báo cáo của lần chạy trước để không lấy nhầm kết quả cũ khi Maven lỗi.
rm -f "$REPORTS"/*com.nextturn.lab3.* 2>/dev/null || true
(cd "$BACKEND" && sh ./mvnw -q test '-Dtest=com.nextturn.lab3.*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=true 2>&1) |
  grep -E 'Tests run:|ERROR|BUILD FAILURE' || true

if ! ls "$REPORTS"/*com.nextturn.lab3.* >/dev/null 2>&1; then
  echo "Maven không tạo báo cáo test, xem lỗi ở trên (thường do chưa cài JDK 25)." >&2
  exit 1
fi
rm -rf "$RESULTS"
mkdir -p "$RESULTS"
cp "$REPORTS"/*com.nextturn.lab3.* "$RESULTS"/
echo
echo "Tóm tắt (báo cáo chi tiết trong thư mục results):"
grep -h 'Tests run' "$RESULTS"/*.txt
