package com.chatbot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {

    public static void main(String[] args) {
        String token = System.getenv("TELEGRAM_BOT_TOKEN");
        String geminiKey = System.getenv("GEMINI_API_KEY");

        if (isBlank(token) || isBlank(geminiKey)) {
            System.err.println("Thiếu TELEGRAM_BOT_TOKEN hoặc GEMINI_API_KEY trong biến môi trường.");
            System.exit(1);
        }

        try {
            // Tạm khởi tạo với username rỗng, sẽ lấy thật qua getMe()
            TelegramChatBot bot = new TelegramChatBot(token, "");
            User me = bot.execute(new GetMe());
            String username = me.getUserName();

            // Tạo lại bot với đúng username (cần cho việc nhận diện @tag trong nhóm)
            bot = new TelegramChatBot(token, username);

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            System.out.println("Bot @" + username + " đã khởi động (long polling).");

            // Health server cho Render keep-alive
            int port = parsePort(System.getenv("PORT"), 3000);
            HealthServer.start(port);

            // Giữ tiến trình sống
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Khởi động thất bại: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
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
}
