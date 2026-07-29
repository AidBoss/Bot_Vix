package com.chatbot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {

    /** Số lần thử lại khi khởi động gặp lỗi tạm thời (vd: 409 do instance cũ chưa tắt). */
    private static final int STARTUP_MAX_RETRIES = 10;
    private static final long STARTUP_RETRY_DELAY_MS = 5000L;

    /**
     * Nhịp chờ trước khi instance MỚI bắt đầu poll, để instance CŨ kịp nhận SIGTERM và
     * nhả kết nối getUpdates — giảm chồng lấn gây 409 Conflict. Override bằng env STARTUP_GRACE_MS.
     */
    private static final long STARTUP_GRACE_MS =
            parseLong(System.getenv("STARTUP_GRACE_MS"), 8000L);

    public static void main(String[] args) {
        String token = System.getenv("TELEGRAM_BOT_TOKEN");
        String geminiKey = System.getenv("GEMINI_API_KEY");

        if (isBlank(token) || isBlank(geminiKey)) {
            System.err.println("Thiếu TELEGRAM_BOT_TOKEN hoặc GEMINI_API_KEY trong biến môi trường.");
            System.exit(1);
        }

        try {
            // 1) Health server lên TRƯỚC để Render đánh dấu instance mới "healthy" sớm, nhờ đó
            //    Render gửi SIGTERM cho instance cũ nhanh hơn (shutdown hook con cũ nhả getUpdates).
            int port = parsePort(System.getenv("PORT"), 3000);
            HealthServer.start(port);

            // 2) Chờ một nhịp cho instance cũ kịp nhả kết nối getUpdates, tránh giành nhau gây 409.
            if (STARTUP_GRACE_MS > 0) {
                System.out.println("⏳ Chờ " + STARTUP_GRACE_MS + "ms cho instance cũ nhả kết nối...");
                Thread.sleep(STARTUP_GRACE_MS);
            }

            // 3) Giờ mới đăng ký bot + bắt đầu poll (startWithRetry vẫn là lưới an toàn cho 409 còn sót).
            String username = startWithRetry(token);
            System.out.println("Bot @" + username + " đã khởi động (long polling).");

            // Giữ tiến trình sống
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Khởi động thất bại: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Khởi động bot, thử lại khi gặp lỗi tạm thời (điển hình là 409 Conflict do
     * instance cũ trên Render chưa bị kill xong trong lúc deploy overlap).
     * Trả về username thật của bot.
     */
    private static String startWithRetry(String token) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= STARTUP_MAX_RETRIES; attempt++) {
            try {
                // Tạm khởi tạo với username rỗng, sẽ lấy thật qua getMe()
                TelegramChatBot probe = new TelegramChatBot(token, "");

                // Xóa webhook (nếu lỡ được set) và bỏ backlog để tránh xung đột với getUpdates.
                probe.execute(DeleteWebhook.builder().dropPendingUpdates(true).build());

                User me = probe.execute(new GetMe());
                String username = me.getUserName();

                // Tạo lại bot với đúng username (cần cho việc nhận diện @tag trong nhóm)
                TelegramChatBot bot = new TelegramChatBot(token, username);

                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                BotSession session = botsApi.registerBot(bot);

                // Khởi động scheduler hẹn giờ thông báo Gâu Hôm (17:35 UTC+7)
                GoHomeScheduler.start(bot);

                // Khi Render deploy lại, nó gửi SIGTERM cho instance cũ. Dừng phiên poll
                // ngay lập tức để nhả kết nối getUpdates, tránh 409 Conflict với instance mới.
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        GoHomeScheduler.stop();
                        if (session != null && session.isRunning()) {
                            session.stop();
                            System.out.println("🛑 Đã dừng phiên bot (nhả kết nối Telegram).");
                        }
                        bot.shutdown();
                    } catch (Exception ignore) {
                    }
                }, "bot-shutdown"));

                return username;
            } catch (Exception e) {
                last = e;
                System.err.println("Khởi động thất bại (lần " + attempt + "/" + STARTUP_MAX_RETRIES
                        + "): " + e.getMessage());
                if (attempt < STARTUP_MAX_RETRIES) {
                    Thread.sleep(STARTUP_RETRY_DELAY_MS);
                }
            }
        }
        throw last;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static int parsePort(String value, int def) {
        try {
            return value == null ? def : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long parseLong(String value, long def) {
        try {
            return value == null ? def : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
