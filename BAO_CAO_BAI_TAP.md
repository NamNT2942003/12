# BÁO CÁO BÀI TẬP NGINX & DOCKER (BÀI 1 ĐẾN BÀI 6)

---

## BÀI TẬP 1: XỬ LÝ SỰ CỐ SẬP NGINX (VẬN DỤNG CƠ BẢN)

### 1. Phân tích nguyên nhân lỗi
* **Thông báo lỗi:** `nginx: [emerg] unexpected "proxy_set_header" in /etc/nginx/sites-enabled/app.conf:7`
* **Nguyên nhân:** Cú pháp của Nginx bắt buộc mỗi chỉ thị (*directive*) phải kết thúc bằng dấu chấm phẩy (`;`). Tại dòng số 6, chỉ thị `proxy_pass http://localhost:8080` bị thiếu dấu chấm phẩy `;`. Nginx coi từ khóa `proxy_set_header` ở dòng 7 là tham số thừa của `proxy_pass`, gây ra lỗi cú pháp.

### 2. Cấu hình sau khi sửa (`assignments/bai1_nginx_syntax/app.conf`)
```nginx
server {
    listen 80;
    server_name example.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 3. Lệnh kiểm tra cú pháp và khởi động lại dịch vụ
```bash
# 1. Kiểm tra cú pháp cấu hình
sudo nginx -t

# 2. Khởi động lại dịch vụ (nếu Nginx đang bị dừng)
sudo systemctl restart nginx
# Hoặc reload không gián đoạn kết nối (Zero Downtime)
sudo systemctl reload nginx
```

---

## BÀI TẬP 2: TỐI ƯU TIMEOUT CHO PROXY (VẬN DỤNG CƠ BẢN)

### 1. Phân tích nguyên nhân lỗi
* Request export báo cáo cần 90 giây để hoàn thành, nhưng Nginx mặc định có giá trị `proxy_read_timeout` là 60 giây. Sau 60 giây không nhận được dữ liệu phản hồi từ backend, Nginx tự ngắt kết nối và trả về lỗi `504 Gateway Time-out`.

### 2. Cấu hình sau khi cập nhật (`assignments/bai2_nginx_timeout/app.conf`)
```nginx
server {
    listen 80;
    server_name api.example.com;

    location /api/export/ {
        proxy_pass http://localhost:8080;

        # Cấu hình chờ backend phản hồi lên tới 120s
        proxy_read_timeout 120s;
        proxy_connect_timeout 120s;
        proxy_send_timeout 120s;
    }
}
```

### 3. Giải thích ý nghĩa các thông số Timeout
* `proxy_read_timeout 120s`: Thời gian tối đa Nginx chờ đọc phản hồi từ backend giữa hai lần đọc liên tiếp (khắc phục trực tiếp lỗi 504 khi backend xử lý 90s).
* `proxy_connect_timeout 120s`: Thời gian tối đa thiết lập kết nối TCP (3-way handshake) ban đầu tới backend.
* `proxy_send_timeout 120s`: Thời gian tối đa Nginx chờ truyền dữ liệu request sang backend giữa hai lần gửi liên tiếp.

---

## BÀI TẬP 3: CẤU HÌNH HEADER SECURITY (VẬN DỤNG CHUYÊN SÂU)

### 1. Cấu hình Nginx hoàn chỉnh (`assignments/bai3_security_headers/secure.conf`)
```nginx
server {
    listen 443 ssl;
    server_name secure.example.com;

    ssl_certificate /etc/nginx/ssl/server.crt;
    ssl_certificate_key /etc/nginx/ssl/server.key;

    # Cấu hình HTTP Security Headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 2. Giải thích ý nghĩa bảo mật
* `X-Frame-Options: SAMEORIGIN`: Ngăn chặn tấn công Clickjacking, chỉ cho phép trang web được nhúng vào thẻ iframe từ chính domain đó.
* `X-Content-Type-Options: nosniff`: Ngăn chặn trình duyệt tự ý phỏng đoán MIME type (chống tấn công MIME-sniffing).
* `Strict-Transport-Security: max-age=31536000; includeSubDomains`: Ép buộc trình duyệt chỉ kết nối bằng giao thức HTTPS trong vòng 1 năm, chống tấn công Man-in-the-Middle và SSL stripping.

### 3. Câu lệnh kiểm chứng bằng `curl -I`
```bash
curl -I https://secure.example.com
```

---

## BÀI TẬP 4: TÍCH HỢP UFW VÀ DOCKER (VẬN DỤNG CHUYÊN SÂU)

### 1. Phân tích sự cố
* Docker can thiệp trực tiếp vào bảng `iptables` (chuỗi `PREROUTING`/`DOCKER`) trước chuỗi `INPUT` của UFW. Vì vậy quy tắc chặn cổng 8080 trên UFW bị bỏ qua khi container bind ra `0.0.0.0:8080`.

### 2. Cấu hình `docker-compose.yml` mới (`assignments/bai4_ufw_docker/docker-compose.yml`)
```yaml
version: '3'
services:
  backend:
    image: my-spring-boot-app:latest
    ports:
      - "127.0.0.1:8080:8080"
    restart: always
```

### 3. Giải thích cơ chế ngăn chặn truy cập trái phép
* Bằng cách chỉ định `127.0.0.1:8080:8080`, Docker chỉ lắng nghe trên card mạng ảo nội bộ (*Local Loopback*).
* Nginx trên cùng máy chủ kết nối qua localhost (`127.0.0.1:8080`) vẫn hoạt động bình thường.
* Lưu lượng từ Internet đi vào qua card mạng Public sẽ bị hệ điều hành từ chối kết nối ngay lập tức (`Connection refused`), không bị ảnh hưởng bởi việc Docker can thiệp vào iptables.

---

## BÀI TẬP 5: PHÂN TÍCH LOG TRUY CẬP NÂNG CAO (PHÂN TÍCH)

### 1. Chuỗi Pipeline Linux tìm Top 5 IP lỗi 404 (`assignments/bai5_log_analysis/analyze_log.sh`)
```bash
grep ' 404 ' /var/log/nginx/access.log | awk '{print $1}' | sort | uniq -c | sort -nr | head -n 5
```

### 2. Mô tả tác dụng của từng lệnh trong Pipeline
* `grep ' 404 ' /var/log/nginx/access.log`: Lọc các dòng log chứa mã trạng thái HTTP 404.
* `awk '{print $1}'`: Trích xuất cột đầu tiên của dòng log (địa chỉ IP của client).
* `sort`: Sắp xếp các IP theo thứ tự alphabet để chuẩn bị cho lệnh `uniq`.
* `uniq -c`: Gom nhóm các IP trùng nhau và đếm số lần xuất hiện của mỗi IP.
* `sort -nr`: Sắp xếp giảm dần theo giá trị số của số lần xuất hiện (IP có nhiều request lỗi 404 nhất lên đầu).
* `head -n 5`: Lấy 5 dòng đầu tiên tương ứng với Top 5 IP vi phạm nhiều nhất.

---

## BÀI TẬP 6: THIẾT KẾ MÔ HÌNH MẠNG BẢO MẬT (SÁNG TẠO)

### 1. Cấu hình `docker-compose.yml` hoàn chỉnh (`assignments/bai6_secure_network/docker-compose.yml`)
```yaml
version: '3.8'

services:
  gateway:
    image: nginx:alpine
    container_name: api-gateway
    restart: always
    ports:
      - "80:80"  # Cổng duy nhất được công khai ra ngoài Internet
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    networks:
      - app-network
    depends_on:
      - user-service
      - order-service

  user-service:
    image: my-user-api:latest
    container_name: user-service
    restart: always
    expose:
      - "8080"   # Mở cổng nội bộ trong Docker network, không mở ra host
    environment:
      - DB_HOST=db
      - DB_PORT=5432
    networks:
      - app-network
    depends_on:
      - db

  order-service:
    image: my-order-api:latest
    container_name: order-service
    restart: always
    expose:
      - "8080"
    environment:
      - DB_HOST=db
      - DB_PORT=5432
    networks:
      - app-network
    depends_on:
      - db

  db:
    image: postgres:15-alpine
    container_name: postgres-db
    restart: always
    environment:
      POSTGRES_DB: app_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: secure_password_here
    volumes:
      - db_data:/var/lib/postgresql/data
    networks:
      - app-network

networks:
  app-network:
    driver: bridge

volumes:
  db_data:
```

### 2. Mô tả giải pháp Internal Network & Docker DNS
* **Internal Network:** Chỉ duy nhất container `gateway` được cấu hình `ports: - "80:80"`. Các container `user-service`, `order-service` và `db` chỉ giao tiếp bên trong mạng ảo `app-network`, ngăn chặn hoàn toàn việc truy cập trái phép từ Internet.
* **Cơ chế Docker DNS:** Trong custom bridge network, Docker tích hợp sẵn DNS server nội bộ (`127.0.0.11`). Khi Nginx cấu hình `proxy_pass http://user-service:8080;`, Nginx gửi truy vấn phân giải tên máy chủ `user-service` tới Docker DNS. Docker DNS tự động trả về IP private của container `user-service` trong mạng `app-network`, giúp gateway kết nối chính xác và an toàn.
