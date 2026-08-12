package com.chatbot;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class GoHomeScheduler {

    public static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh"); // UTC+7 Việt Nam

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> currentTask = null;
    private static Long targetChatId = null;

    // Giờ thông báo ngày thường (Thứ 2 - Thứ 6)
    private static int currentHour = 17;
    private static int currentMinute = 35;

    // Giờ thông báo Thứ 7 (tuần đầu & tuần cuối tháng) -> 17:00 (5h chiều)
    private static int saturdayHour = 17;
    private static int saturdayMinute = 0;

    private static ZonedDateTime nextRunTime = null;

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

    public static int getHour() {
        return currentHour;
    }

    public static int getMinute() {
        return currentMinute;
    }

    public static int getSatHour() {
        return saturdayHour;
    }

    public static int getSatMinute() {
        return saturdayMinute;
    }

    public static ZonedDateTime getNextRunTime() {
        return nextRunTime;
    }

    public static String getNextRunTimeFormatted() {
        if (nextRunTime == null) return null;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm - EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
        return nextRunTime.format(fmt);
    }

    /**
     * Kiểm tra xem ngày thứ Bảy có phải là thứ 7 đầu tiên hoặc thứ 7 cuối cùng trong tháng hay không.
     * (Các thứ 7 ở giữa tháng sẽ trả về false).
     */
    public static boolean isWorkSaturday(LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            return false;
        }
        LocalDate firstSat = date.with(TemporalAdjusters.firstInMonth(DayOfWeek.SATURDAY));
        LocalDate lastSat = date.with(TemporalAdjusters.lastInMonth(DayOfWeek.SATURDAY));
        return date.equals(firstSat) || date.equals(lastSat);
    }

    /**
     * Kiểm tra xem một ngày có được gửi thông báo Gâu Hôm hay không.
     * - Chủ nhật: KHÔNG gửi.
     * - Thứ 7: chỉ gửi vào 2 thứ 7 đầu & cuối tháng (2 thứ 7 giữa tháng không báo).
     * - Thứ 2 đến Thứ 6: gửi bình thường.
     */
    public static boolean shouldNotifyOnDate(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SUNDAY) {
            return false;
        }
        if (dow == DayOfWeek.SATURDAY) {
            return isWorkSaturday(date);
        }
        return true;
    }

    /**
     * Lấy giờ thông báo theo ngày:
     * - Thứ 7: satHour:satMinute (mặc định 17:00).
     * - Thứ 2 - Thứ 6: weekdayHour:weekdayMinute (mặc định 17:35).
     */
    public static LocalTime getNotificationTimeForDate(LocalDate date, int weekdayH, int weekdayM, int satH, int satM) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return LocalTime.of(satH, satM);
        }
        return LocalTime.of(weekdayH, weekdayM);
    }

    /**
     * Tìm thời điểm gửi thông báo tiếp theo từ mốc thời gian {@code from}.
     */
    public static ZonedDateTime calculateNextRun(ZonedDateTime from, int weekdayH, int weekdayM, int satH, int satM) {
        ZoneId zone = from.getZone();
        LocalDate startDate = from.toLocalDate();

        for (int i = 0; i < 35; i++) {
            LocalDate date = startDate.plusDays(i);
            if (shouldNotifyOnDate(date)) {
                LocalTime time = getNotificationTimeForDate(date, weekdayH, weekdayM, satH, satM);
                ZonedDateTime candidate = date.atTime(time).atZone(zone);
                if (candidate.isAfter(from)) {
                    return candidate;
                }
            }
        }
        return from.plusDays(1).withHour(weekdayH).withMinute(weekdayM).withSecond(0).withNano(0);
    }

    /**
     * Lên lịch cho lần gửi thông báo tiếp theo.
     */
    public static synchronized void scheduleNext(TelegramChatBot bot) {
        if (currentTask != null) {
            currentTask.cancel(false);
        }

        ZonedDateTime now = ZonedDateTime.now(ZONE_VN);
        ZonedDateTime nextRun = calculateNextRun(now, currentHour, currentMinute, saturdayHour, saturdayMinute);
        nextRunTime = nextRun;

        long delaySeconds = Duration.between(now, nextRun).getSeconds();
        if (delaySeconds < 1) {
            delaySeconds = 1;
        }
        long delayMinutes = delaySeconds / 60;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
        System.out.printf("⏰ Đã lên lịch thông báo Gâu Hôm tiếp theo: %s (sau %d phút, %d giây).%n",
                nextRun.format(fmt), delayMinutes, delaySeconds);

        currentTask = scheduler.schedule(() -> {
            try {
                if (targetChatId != null) {
                    sendNotification(bot, targetChatId);
                } else {
                    System.out.println("⚠️ Chưa có Chat ID nhóm để gửi thông báo Gâu Hôm (dùng env GAU_HOM_CHAT_ID hoặc /setgauhom).");
                }
            } catch (Exception e) {
                System.err.println("Lỗi khi gửi thông báo Gâu Hôm: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Tự động lên lịch cho lần tiếp theo
                scheduleNext(bot);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public static synchronized void reschedule(TelegramChatBot bot, int hour, int minute) {
        currentHour = hour;
        currentMinute = minute;
        scheduleNext(bot);
    }

    public static void start(TelegramChatBot bot) {
        currentHour = parseEnvInt("GAU_HOM_HOUR", 17);
        currentMinute = parseEnvInt("GAU_HOM_MINUTE", 35);
        saturdayHour = parseEnvInt("GAU_HOM_SAT_HOUR", 17);
        saturdayMinute = parseEnvInt("GAU_HOM_SAT_MINUTE", 0);
        scheduleNext(bot);
    }

    private static final List<String> MESSAGES = List.of(
            "🔔 **HẾT GIỜ LÀM VIỆC RỒI CÁC CON VỢ ƠI!** 🏃‍♂️💨\n\nCống hiến thế là tuyệt rồi!",
            "🔔 **TỚI GIỜ COOK RỒI ANH EM Ê!** 🏃‍♂️💨\n\nGập laptop lại, đi nhậu thôi!",
            "🔔 **ALO ALO! ĐỒNG HỒ ĐÃ ĐIỂM GIỜ VỀ!** ⏰\n\nBug để mai fix, code để mai push!",
            "🔔 **HẾT GIỜ BÀO RỒI ANH EM ƠI!** 🛵💨\n\nLương 5 củ đừng làm nhiệt huyết như 50 củ. Chấm công lẹ kẻo quên rồi cook thôi!"
    );

    public static void sendNotification(TelegramChatBot bot, long chatId) {
        String msg = MESSAGES.get(ThreadLocalRandom.current().nextInt(MESSAGES.size()));
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
