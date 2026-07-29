package com.chatbot;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class TelegramChatBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final GeminiService gemini = new GeminiService();
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public TelegramChatBot(String token, String botUsername) {
        super(token);
        this.botUsername = botUsername;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public GeminiService getGemini() {
        return gemini;
    }

    public void shutdown() {
        System.out.println("🤖 Đang dừng Thread Pool của bot...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("🤖 Đã dừng Thread Pool thành công.");
    }

    @Override
    public void onUpdateReceived(Update update) {
        executor.submit(() -> {
            try {
                processUpdate(update);
            } catch (Exception e) {
                System.err.println("Lỗi không mong muốn trong Executor: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void processUpdate(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Message msg = update.getMessage();
        String text = msg.getText().trim();
        long chatId = msg.getChatId();

        try {
            // ----- Lệnh -----
            if (text.startsWith("/")) {
                handleCommand(text, msg, chatId);
                return;
            }

            User from = msg.getFrom();
            long userId = from.getId();
            String userName = firstNonBlank(from.getFirstName(), from.getUserName(), "bạn");
            String chatType = msg.getChat().getType(); // private | group | supergroup
            String content = text;

            // Trong nhóm: chỉ phản hồi khi được tag @bot hoặc reply tin của bot
            if ("group".equals(chatType) || "supergroup".equals(chatType)) {
                boolean mentioned = content.contains("@" + botUsername);
                boolean replyToBot = msg.isReply()
                        && msg.getReplyToMessage().getFrom() != null
                        && botUsername.equals(msg.getReplyToMessage().getFrom().getUserName());
                if (!mentioned && !replyToBot) {
                    // Âm thầm ghi lại tin nhắn nhóm vào lịch sử
                    gemini.recordMessage(chatId, userName, content);
                    return;
                }
                content = content.replace("@" + botUsername, "").trim();
            }

            if (content.isBlank()) return;

            sendTyping(chatId);

            List<String> responses = gemini.chat(content, chatId, userId, userName);
            for (String response : responses) {
                if (response == null || response.isBlank()) continue;
                reply(chatId, msg.getMessageId(), response.trim());
            }

        } catch (Exception e) {
            System.err.println("Lỗi xử lý tin nhắn: " + e.getMessage());
            try {
                reply(chatId, msg.getMessageId(), errorReply(e));
            } catch (Exception ignore) {
            }
        }
    }

    private void handleCommand(String text, Message msg, long chatId) throws TelegramApiException {
        String cmd = text.split("\\s+")[0].split("@")[0].toLowerCase();
        switch (cmd) {
            case "/start" -> send(chatId,
                    "👋 Chào cậu! Mình là *" + GeminiService.botName() + "* đây~\n\n" +
                    "💬 Cứ nhắn tin để tụi mình tám chuyện nha!\n" +
                    "🔎 /search <từ khóa> — tra Google lấy thông tin mới nhất\n" +
                    "🧹 /clear — xóa lịch sử trò chuyện\n" +
                    "📝 /tomtat — tóm tắt nội dung trò chuyện gần đây\n" +
                    "📊 /status — xem trạng thái bot\n" +
                    "🆔 /chatid — xem ID nhóm này");
            // Các lệnh quản trị Gâu Hôm & Xưng Hô (chỉ owner mới dùng được)
            case "/search" -> handleSearch(text, msg, chatId);
            case "/tomtat" -> handleSummary(text, msg, chatId);
            case "/clear" -> {
                gemini.clearMemory(chatId);
                send(chatId, "🧹 Xong! Mình xóa lịch sử rồi, mình bắt đầu lại từ đầu nha 😊");
            }
            case "/status" -> send(chatId,
                    "📊 *Trạng thái bot*\n👥 Số phiên đang hoạt động: " + gemini.activeSessions() +
                    "\n⏰ Nhóm Gâu Hôm Chat ID: `" + (GoHomeScheduler.getTargetChatId() != null ? GoHomeScheduler.getTargetChatId() : "Chưa cài đặt") + "`");
            case "/chatid" -> send(chatId, "🆔 Chat ID của nhóm/cuộc trò chuyện này là: `" + chatId + "`");
            case "/setgauhom" -> {
                if (!GeminiService.isOwner(msg.getFrom().getId())) {
                    reply(chatId, msg.getMessageId(), "⛔ Lệnh này chỉ dành riêng cho đại ka Đức Anh nha.");
                    return;
                }
                GoHomeScheduler.setTargetChatId(chatId);
                send(chatId, "✅ Đã đặt nhóm này (`" + chatId + "`) làm nơi nhận thông báo **Gâu Hôm** vào 5h35 chiều hàng ngày!");
            }
            case "/stopgauhom", "/cleargauhom", "/unsetgauhom" -> {
                if (!GeminiService.isOwner(msg.getFrom().getId())) {
                    reply(chatId, msg.getMessageId(), "⛔ Lệnh này chỉ dành riêng cho đại ka Đức Anh nha.");
                    return;
                }
                GoHomeScheduler.clearTargetChatId();
                send(chatId, "🔕 Đã tắt thông báo **Gâu Hôm**. Bot sẽ không gửi tin nhắn tự động nữa.");
            }
            case "/testgauhom" -> {
                if (!GeminiService.isOwner(msg.getFrom().getId())) {
                    reply(chatId, msg.getMessageId(), "⛔ Lệnh này chỉ dành riêng cho đại ka Đức Anh nha.");
                    return;
                }
                send(chatId, "🧪 Đang thử nghiệm gửi thông báo Gâu Hôm...");
                GoHomeScheduler.sendNotification(this, chatId);
            }
            case "/xungho" -> handleXungHo(text, msg, chatId);
            default -> { /* lệnh lạ thì bỏ qua */ }
        }
    }

    /** /search <từ khóa> — ép bot tra Google rồi trả lời. */
    private void handleSearch(String text, Message msg, long chatId) throws TelegramApiException {
        // Bỏ phần "/search" (kèm "@botname" nếu có) để lấy từ khóa.
        String query = text.replaceFirst("(?i)^/search(@\\S+)?\\s*", "").trim();
        if (query.isBlank()) {
            send(chatId, "🔎 Cậu muốn tra gì? Gõ kiểu: `/search giá vàng hôm nay` nha.");
            return;
        }

        User from = msg.getFrom();
        long userId = from.getId();
        String userName = firstNonBlank(from.getFirstName(), from.getUserName(), "bạn");

        sendTyping(chatId);
        try {
            List<String> responses = gemini.chat(query, chatId, userId, userName, true);
            for (String response : responses) {
                if (response == null || response.isBlank()) continue;
                reply(chatId, msg.getMessageId(), response.trim());
            }
        } catch (Exception e) {
            System.err.println("Lỗi /search: " + e.getMessage());
            reply(chatId, msg.getMessageId(), errorReply(e));
        }
    }

    /** /tomtat — tóm tắt nội dung cuộc trò chuyện gần đây trong nhóm. */
    private void handleSummary(String text, Message msg, long chatId) throws TelegramApiException {
        User from = msg.getFrom();
        long userId = from.getId();
        String userName = firstNonBlank(from.getFirstName(), from.getUserName(), "bạn");

        sendTyping(chatId);
        try {
            List<String> responses = gemini.getSummary(chatId, userId, userName);
            for (String response : responses) {
                if (response == null || response.isBlank()) continue;
                reply(chatId, msg.getMessageId(), response.trim());
            }
        } catch (Exception e) {
            System.err.println("Lỗi /tomtat: " + e.getMessage());
            reply(chatId, msg.getMessageId(), errorReply(e));
        }
    }

    /**
     * /xungho &lt;userId&gt; &lt;cách xưng hô&gt; — đặt cách bot gọi một người (Chỉ quản trị viên).
     * Bỏ trống phần cách xưng hô để xoá. Nếu reply vào tin của ai đó thì lấy luôn id người đó.
     */
    private void handleXungHo(String text, Message msg, long chatId) throws TelegramApiException {
        long requesterId = msg.getFrom().getId();
        if (!GeminiService.isAdmin(requesterId)) {
            reply(chatId, msg.getMessageId(), "⛔ Bạn không có quyền thiết lập cách xưng hô nha.");
            return;
        }

        // Bỏ "/xungho" (kèm "@botname" nếu có) -> còn lại phần tham số.
        String args = text.replaceFirst("(?i)^/xungho(@\\S+)?\\s*", "").trim();

        Long targetId = null;
        String nick;
        String targetUsername = null;   // để bot còn nhận ra khi ai hỏi "@name là ai"
        String targetName = null;

        // Cách 1: reply vào tin của người cần đặt -> dùng id người đó, args là cách xưng hô.
        if (msg.isReply() && msg.getReplyToMessage().getFrom() != null) {
            User target = msg.getReplyToMessage().getFrom();
            targetId = target.getId();
            targetUsername = target.getUserName();
            targetName = target.getFirstName();
            nick = args;
        } else {
            // Cách 2: /xungho <userId> <cách xưng hô...>
            String[] parts = args.split("\\s+", 2);
            if (parts[0].isBlank()) {
                send(chatId, "✍️ Cú pháp: `/xungho <userId> <cách xưng hô>`\n"
                        + "Hoặc reply vào tin của người đó rồi gõ `/xungho <cách xưng hô>`.\n"
                        + "Bỏ trống cách xưng hô để xoá.");
                return;
            }
            try {
                targetId = Long.parseLong(parts[0].trim());
            } catch (NumberFormatException e) {
                send(chatId, "⚠️ userId phải là số. Cú pháp: `/xungho <userId> <cách xưng hô>`.");
                return;
            }
            nick = (parts.length > 1) ? parts[1].trim() : "";
        }

        if (GeminiService.isOwner(targetId) && requesterId != targetId) {
            reply(chatId, msg.getMessageId(), "⛔ Bạn không thể thiết lập cách xưng hô cho đại ka Đức Anh.");
            return;
        }

        String saved = gemini.setAddressing(targetId, nick, targetUsername, targetName);
        if (saved == null) {
            send(chatId, "🧽 Đã xoá cách xưng hô cho user `" + targetId + "`.");
        } else {
            send(chatId, "✅ Từ giờ mình sẽ gọi user `" + targetId + "` là *" + saved + "*.");
        }
    }

    private void sendTyping(long chatId) {
        try {
            execute(SendChatAction.builder()
                    .chatId(String.valueOf(chatId))
                    .action("typing")
                    .build());
        } catch (TelegramApiException ignore) {
        }
    }

    /** Gửi tin có reply; nếu Markdown lỗi parse thì gửi lại dạng thường. */
    private void reply(long chatId, Integer replyTo, String text) throws TelegramApiException {
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .replyToMessageId(replyTo)
                .parseMode(ParseMode.MARKDOWN)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            message.setParseMode(null);
            execute(message);
        }
    }

    public void send(long chatId, String text) throws TelegramApiException {
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode(ParseMode.MARKDOWN)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            message.setParseMode(null);
            execute(message);
        }
    }

    /** Chọn message lỗi phù hợp: phân biệt quá tải quota (429) với lỗi khác. */
    private static String errorReply(Exception e) {
        if (GeminiService.isQuotaError(e)) {
            return "Mây đang bị quá tải (hết lượt gọi rồi) 😴 Đợi mình chút rồi nhắn lại nha.";
        }
        return "Có lỗi xảy ra rồi 😢 Bạn thử lại sau giúp mình nha.";
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "bạn";
    }
}
