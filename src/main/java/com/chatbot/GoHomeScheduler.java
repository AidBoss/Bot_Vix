package com.chatbot;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.util.List;

public class GoHomeScheduler {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static Long targetChatId = null;

    static {
        String envChatId = System.getenv("GAU_HOM_CHAT_ID");
        if (envChatId != null && !envChatId.isBlank()) {
            try {
                targetChatId = Long.parseLong(envChatId.trim());
                System.out.println("📌 Đã tải GAU_HOM_CHAT_ID từ môi trường: " + targetChatId);
            } catch (NumberFormatException e) {
                System.err.println("⚠️ GAU_HOM_CHAT_ID không hợp lệ: " + envChatId);
            }
        }
    }

    public static void setTargetChatId(long chatId) {
        targetChatId = chatId;
        System.out.println("📌 Đã cập nhật Chat ID nhận tin Gâu Hôm: " + chatId);
    }

    public static void clearTargetChatId() {
        targetChatId = null;
        System.out.println("📌 Đã tắt/xoá nhận thông báo Gâu Hôm.");
    }

    public static Long getTargetChatId() {
        return targetChatId;
    }

    public static void start(TelegramChatBot bot) {
        int hour = parseEnvInt("GAU_HOM_HOUR", 17);
        int minute = parseEnvInt("GAU_HOM_MINUTE", 35);

        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        if (now.compareTo(target) >= 0) {
            target = target.plusDays(1);
        }

        long initialDelaySeconds = Duration.between(now, target).getSeconds();
        long initialDelayMinutes = initialDelaySeconds / 60;

        System.out.printf("⏰ Đã lên lịch thông báo Gâu Hôm (%02d:%02d GMT+7). Lần chạy tiếp theo sau %d phút (%s).%n",
                hour, minute, initialDelayMinutes, target);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (targetChatId != null) {
                    sendNotification(bot, targetChatId);
                } else {
                    System.out.println("⚠️ Chưa có Chat ID nhóm để gửi thông báo Gâu Hôm (dùng env GAU_HOM_CHAT_ID hoặc /setgauhom).");
                }
            } catch (Exception e) {
                System.err.println("Lỗi khi gửi thông báo Gâu Hôm: " + e.getMessage());
                e.printStackTrace();
            }
        }, initialDelaySeconds, 24 * 3600, TimeUnit.SECONDS);
    }

    public static void sendNotification(TelegramChatBot bot, long chatId) {
        List<String> mentions = (bot != null && bot.getGemini() != null)
                ? bot.getGemini().getAddressingMentions()
                : List.of();

        String tagText = mentions.isEmpty()
                ? "mọi người"
                : String.join(" ", mentions);

        String msg = "🔔 **ĐẾN GIỜ GÂU HÔM RỒI MỌI NGƯỜI ƠI!** 🏃‍♂️💨\n\n" +
                      tagText + " và các Bot thu dọn đồ đạc vè thôi nào! 🏡✨";
        try {
            bot.send(chatId, msg);
            System.out.println("✅ Đã gửi thông báo Gâu Hôm tới chat: " + chatId);
        } catch (Exception e) {
            System.err.println("❌ Không thể gửi thông báo Gâu Hôm tới chat " + chatId + ": " + e.getMessage());
        }
    }

    public static void stop() {
        System.out.println("⏰ Đang dừng GoHomeScheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static int parseEnvInt(String key, int defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
