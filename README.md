# Báo cáo Bài 6: Xây dựng Workflow CI hoàn chỉnh cho hệ thống Microservices

## 1. Bối cảnh & Mục tiêu
- **Dự án**: QuickBite Microservices Platform.
- **Service**: `order-service`.
- **Mục tiêu**: Thiết kế một pipeline CI hoàn chỉnh trong `.github/workflows/main.yml`, tổng hợp các kỹ thuật hiện đại:
  - Điều phối **2 Job chạy song song (Parallel)**: Kiểm tra chất lượng code (`code-quality`) và Kiểm thử Unit Test (`unit-test`).
  - Sử dụng **GitHub Actions Service Container** để tự động khởi tạo database PostgreSQL tạm thời phục vụ cho bước Unit Test.
  - Điều phối **1 Job chạy tuần tự (Sequential)**: Đóng gói (`build`) file JAR sau khi cả 2 job kiểm tra trên đều vượt qua (`needs: [code-quality, unit-test]`).
  - Quản lý và lưu trữ file JAR dạng **Artifact với thời hạn 7 ngày** (`retention-days: 7`).

---

## 2. Sơ đồ kiến trúc Pipeline CI

```text
       ┌────────────────────────┐
       │     git push/PR        │
       └───────────┬────────────┘
                   │
         ┌─────────┴─────────┐
         │ (Chạy song song)  │
         ▼                   ▼
┌──────────────────┐ ┌───────────────────────────┐
│  code-quality    │ │        unit-test          │
│ (./gradlew check)│ │ ┌───────────────────────┐ │
└────────┬─────────┘ │ │ PostgreSQL Container   │ │
         │           │ │ (postgres:15-alpine)  │ │
         │           │ └───────────▲───────────┘ │
         │           │             │             │
         │           │   (./gradlew test)        │
         │           └─────────────┬─────────────┘
         │                         │
         └────────────┬────────────┘
                      │ (needs: [code-quality, unit-test])
                      ▼
             ┌──────────────────┐
             │      build       │
             │(./gradlew bootJar│
             │   Upload JAR)    │
             └──────────────────┘
```

---

## 3. Phân tích các thành phần kỹ thuật quan trọng

### 🐘 1. PostgreSQL Service Container (`services.postgres`)
- **Vấn đề**: Các bài Unit & Integration Test của `order-service` cần thao tác cơ sở dữ liệu thật nhưng không được làm phụ thuộc vào database ngoài production.
- **Giải pháp**: Khai báo block `services:` ngay trong job `unit-test`:
  ```yaml
  services:
    postgres:
      image: postgres:15-alpine
      env:
        POSTGRES_DB: quickbite_order_db
        POSTGRES_USER: quickbite
        POSTGRES_PASSWORD: password123
      ports:
        - 5432:5432
      options: >-
        --health-cmd pg_isready
        --health-interval 10s
        --health-timeout 5s
        --health-retries 5
  ```
- **Cơ chế**: Runner của GitHub Actions tự động kéo image `postgres:15-alpine`, ánh xạ cổng `5432:5432` và chờ đến khi container sẵn sàng (`pg_isready`) mới thực thi các step tiếp theo. Step test kết nối trực tiếp qua `localhost:5432`.

### ⚡ 2. Thực thi song song (Parallel Execution)
- Hai job `code-quality` và `unit-test` không chứa từ khóa `needs`, cho phép GitHub Actions phân phối chạy đồng thời trên 2 runner độc lập, giúp rút ngắn thời gian pipeline tối đa.

### 🛡️ 3. Kiểm soát chất lượng trước khi đóng gói (`needs: [code-quality, unit-test]`)
- Job `build` được ràng buộc bằng cú pháp:
  ```yaml
  needs: [code-quality, unit-test]
  ```
- Nếu bất kỳ job nào trong hai job trên bị lỗi (ví dụ: vi phạm chuẩn code hoặc Unit Test fail), job `build` sẽ tự động bị bỏ qua (`skipped`), ngăn chặn việc tạo ra artifact lỗi.

### 📦 4. Quản lý Artifact (Lưu trữ 7 ngày)
- Sử dụng action chính thức `actions/upload-artifact@v4` với cấu hình:
  ```yaml
  with:
    name: order-service-artifact
    path: build/libs/*.jar
    retention-days: 7
  ```
- Thời gian lưu trữ được đặt chính xác 7 ngày theo đúng yêu cầu nghiệp vụ của đề bài.

---

## 4. Hướng dẫn Push lên GitHub & Nộp bài

1. **Tạo repository mới trên GitHub**:
   - Truy cập: **https://github.com/new**
   - Tên Repository: `12` (hoặc `quickbite-order-service`)
   - Chọn **Public** -> Nhấn **Create repository**.

2. **Push mã nguồn từ thư mục `C:\Devop\12`**:
   ```bash
   cd C:\Devop\12
   git remote add origin https://github.com/NamNT2942003/12.git
   git push -u origin main
   ```

3. **Kiểm tra kết quả**:
   - Mở tab **Actions** tại: `https://github.com/NamNT2942003/12/actions`
   - Đồ thị trực quan sẽ hiển thị rõ:
     - 2 job `Code Quality Check` và `Unit & Integration Tests` chạy song song.
     - Sau khi cả 2 hoàn tất, job `Build & Package Artifact` chạy và hoàn thành tích xanh.
     - Mục **Artifacts** ở cuối trang hiển thị `order-service-artifact` sẵn sàng tải về.
4. Copy link run thành công hoặc link repository để nộp bài trên Portal.
