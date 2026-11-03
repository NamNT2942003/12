#!/usr/bin/env bash
# Pipeline phân tích access.log tìm Top 5 địa chỉ IP có nhiều request lỗi 404 nhất

LOG_FILE="/var/log/nginx/access.log"

if [ -f "$1" ]; then
    LOG_FILE="$1"
fi

echo "=== TOP 5 IP GỬI REQUEST LỖI 404 NHIỀU NHẤT ==="
grep ' 404 ' "$LOG_FILE" | awk '{print $1}' | sort | uniq -c | sort -nr | head -n 5
