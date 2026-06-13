# Gemini Telegram Bot (Java)

Chatbot Telegram viết bằng **Java 17**, tích hợp **Gemini** (mặc định `gemini-3.1-flash-lite`),
trò chuyện tự nhiên như một người bạn. Port từ project Node.js sang Java.

## Tính năng
- Long polling (không cần webhook / domain).
- Nhớ ngữ cảnh hội thoại theo từng user (10 lượt gần nhất), tự dọn session sau 30 phút không hoạt động.
- Trong nhóm: chỉ trả lời khi được `@tag` hoặc reply tin của bot.
- Tự chia tin nhắn dài cho hợp giới hạn 4096 ký tự của Telegram.
- Health server (`/health`) để keep-alive trên Render.
- Lệnh: `/start`, `/clear`, `/status`.

## Cấu trúc
```
src/main/java/com/chatbot/
  Main.java              # entrypoint, đăng ký bot + health server
  TelegramChatBot.java   # xử lý update, lệnh, logic nhóm
  GeminiService.java     # gọi Gemini REST API + quản lý trí nhớ
  HealthServer.java      # HTTP health check
pom.xml                  # build fat jar (maven-shade)
Dockerfile               # build & run cho Render
render.yaml              # cấu hình deploy Render
```

## Chạy local
1. Lấy `TELEGRAM_BOT_TOKEN` từ [@BotFather](https://t.me/BotFather).
2. Lấy `GEMINI_API_KEY` từ [Google AI Studio](https://aistudio.google.com/apikey).
3. Export biến môi trường rồi build & run:
```bash
export TELEGRAM_BOT_TOKEN=xxx
export GEMINI_API_KEY=xxx
mvn clean package
java -jar target/bot.jar
```

## Đổi tính cách bot
Sửa `DEFAULT_SYSTEM_PROMPT` trong `GeminiService.java`, hoặc set biến môi trường
`SYSTEM_PROMPT` (và `BOT_NAME`) để override mà không cần sửa code.

## Deploy lên Render
**Cách A — dùng render.yaml (Blueprint):**
1. Push code lên GitHub.
2. Render Dashboard → **New** → **Blueprint** → chọn repo. Render đọc `render.yaml`.
3. Điền `TELEGRAM_BOT_TOKEN` và `GEMINI_API_KEY` ở phần Environment.

**Cách B — tạo Web Service thủ công:**
1. **New** → **Web Service** → connect repo.
2. Runtime: **Docker** (Render tự nhận `Dockerfile`).
3. Thêm env: `TELEGRAM_BOT_TOKEN`, `GEMINI_API_KEY` (và tùy chọn `GEMINI_MODEL`, `BOT_NAME`).
4. Health Check Path: `/health`.

> ⚠️ Gói **free** của Render sẽ ngủ sau 15 phút không có request → bot có thể chậm phản hồi
> lượt đầu sau khi ngủ. Dùng [UptimeRobot](https://uptimerobot.com) ping `/health` mỗi 5–10 phút để giữ bot tỉnh.
