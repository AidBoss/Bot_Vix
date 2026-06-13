package com.chatbot;

import java.util.List;

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

    public TelegramChatBot(String token, String botUsername) {
        super(token);
        this.botUsername = botUsername;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
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
                if (!mentioned && !replyToBot) return;
                content = content.replace("@" + botUsername, "").trim();
            }

            if (content.isBlank()) return;

            sendTyping(chatId);

            List<String> responses = gemini.chat(content, userId, userName);
            for (String response : responses) {
                if (response == null || response.isBlank()) continue;
                reply(chatId, msg.getMessageId(), response.trim());
            }

        } catch (Exception e) {
            System.err.println("Lỗi xử lý tin nhắn: " + e.getMessage());
            try {
                reply(chatId, msg.getMessageId(),
                        "Có lỗi xảy ra rồi 😢 Bạn thử lại sau giúp mình nha.");
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
                    "🧹 /clear — xóa lịch sử trò chuyện\n" +
                    "📊 /status — xem trạng thái bot");
            case "/clear" -> {
                gemini.clearMemory(msg.getFrom().getId());
                send(chatId, "🧹 Xong! Mình xóa lịch sử rồi, mình bắt đầu lại từ đầu nha 😊");
            }
            case "/status" -> send(chatId,
                    "📊 *Trạng thái bot*\n👥 Số phiên đang hoạt động: " + gemini.activeSessions());
            default -> { /* lệnh lạ thì bỏ qua */ }
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

    private void send(long chatId, String text) throws TelegramApiException {
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

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "bạn";
    }
}
