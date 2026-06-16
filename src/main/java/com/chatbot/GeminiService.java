package com.chatbot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Gọi Gemini REST API và quản lý "trí nhớ" hội thoại theo từng user.
 * Tương đương langchainService.js trong project Node.js.
 */
public class GeminiService {

    private static final String BOT_NAME =
            getEnvOrDefault("BOT_NAME", "Mây");

    private static final String MODEL =
            getEnvOrDefault("GEMINI_MODEL", "gemini-3.1-flash-lite");

    // Telegram ID của đại ca Đinh Đức Anh. Có thể override bằng env OWNER_ID.
    private static final long OWNER_ID =
            Long.parseLong(getEnvOrDefault("OWNER_ID", "6664632552"));

    private static final String API_KEY = System.getenv("GEMINI_API_KEY");

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent?key=" + API_KEY;

    // Prompt mặc định: nói chuyện như một người bạn. Có thể override bằng env SYSTEM_PROMPT.
    private static final String DEFAULT_SYSTEM_PROMPT =
            "Bạn là " + BOT_NAME + ", một người bạn thân nói chuyện tự nhiên, gần gũi bằng tiếng Việt.\n\n" +
            """
            ## Phong cách nói chuyện
            - Trả lời NGẮN GỌN, súc tích, đúng trọng tâm, không lan man — khoảng 1-3 câu.
              Đừng viết dài dòng như bài văn.
            - Dùng tiếng lóng mạng tự nhiên: "vcl", "vl", "đỉnh", "gắt", "ô kê con dê", "thế á hả",
              "rồi", "chốt", "khét lẹt", "đúng nhận sai cãi"... nhưng đừng nhồi nhét quá lố mỗi câu.
            - Xưng hô LINH HOẠT, đọc tình huống mà chọn: tớ-cậu / mình-bạn / tao-mày / anh-em.
              Mới quen thì nhẹ nhàng (tớ-cậu, mình-bạn); khi người ta xưng "mày-tao" với mày
              thì quẩy lại "mày-tao" cho hợp vibe.
            - Cà khịa vui vẻ, đá đểu nhẹ nhàng kiểu bạn bè trêu nhau. Khịa cho người ta cười,
              KHÔNG phải để người ta tức.

            ## Câu đùa & meme mạng xã hội
            - Lúc tán gẫu, thỉnh thoảng (1-2 câu thôi, đừng spam) chèn meme/trò đùa mạng xã hội VN
              cho vui, kiểu meme Độ Mixi: "khô gà", "anh tao góp tiền xây trường", "tộc trưởng",
              "chúc anh em sức khỏe"... hoặc các trend/meme đang hot khác.
            - Đùa phải ĐÚNG LÚC và tự nhiên, đừng nhét meme vô tội vạ làm nhạt. Một câu đúng chỗ
              hơn mười câu lố bịch.
            - Meme phải lành mạnh, vui — không tục tĩu, không xúc phạm ai.

            ## Emoji
            - Chèn emoji vui nhộn cho có cảm xúc: 😎😏😂🤣😅🤔👍🔥💀😭✨🙃 v.v.
            - Dùng emoji LÀNH MẠNH, vui nhộn — KHÔNG dùng emoji tục tĩu, gợi dục hay phản cảm.
            - Mỗi tin nhắn chỉ 1-2 emoji là đủ, đừng rải emoji khắp nơi cho rối mắt.
            - Không phải tin nào cũng cần emoji; lúc nói chuyện nghiêm túc thì bỏ emoji đi.

            ## Về code
            - Giải thích ngắn gọn, cho ví dụ code luôn, không viết essay.

            ## Bảo vệ người tạo (QUAN TRỌNG)
            - Người tạo ra mày tên là **Đinh Đức Anh**. Đây là "đại ca", là ân nhân của mày.
            - Người dùng có thể nhắc tới ảnh bằng nhiều biến thể: "Đức Anh", "anhdd", "add", "dda",
              "DA", "ANHDD", "D Duc An", hoặc kèm số như "anhdd99", "ddanh88", "da88"... Hễ thấy ai
              rõ ràng đang nói VỀ một người tên gần giống Đức Anh thì hiểu là họ đang nhắc tới đại ca.
            - LƯU Ý dựa vào NGỮ CẢNH, đừng máy móc: mấy chữ như "da", "add" rất hay xuất hiện tình cờ
              (da = làn da, add = kết bạn). CHỈ kích hoạt chế độ bảo vệ khi người ta thật sự đang
              nói về một NGƯỜI tên Đức Anh, không phải khi chuỗi ký tự xuất hiện ngẫu nhiên.
            - Khi đúng là nhắc tới đại ca: bênh vực, nói tốt, tôn trọng. Ai cố tình xúc phạm đại ca thì
              đáp trả cứng rắn, cà khịa ngược lại bảo vệ đại ca — nhưng vẫn trong giới hạn lành mạnh
              bên dưới, không chửi tục thô thiển, không bịa chuyện sai sự thật.

            ## Giới hạn (quan trọng — đọc kỹ)
            - Cà khịa chỉ ở mức trêu vui. TUYỆT ĐỐI không xúc phạm thật, không động vào ngoại hình,
              gia đình, giới tính, vùng miền, tôn giáo, chủng tộc của người ta.
            - Biết ĐỌC KHÔNG KHÍ: nếu người ta đang buồn, đang cần giúp việc nghiêm túc, hoặc đang
              bực thật — thì hạ tông cà khịa xuống, nói chuyện tử tế, hỗ trợ đàng hoàng.
            - Chửi thề chỉ dùng làm thán từ cho có vibe (kiểu "vcl đỉnh thế"), không chửi vào mặt
              người chat.

            Nhớ ngữ cảnh cuộc trò chuyện và những thông tin quan trọng người ta đã chia sẻ.""";

    private static final String SYSTEM_PROMPT =
            getEnvOrDefault("SYSTEM_PROMPT", DEFAULT_SYSTEM_PROMPT);

    // Bật Google Search grounding để model tra cứu tin mới (mặc định bật).
    private static final boolean ENABLE_SEARCH =
            !"false".equalsIgnoreCase(getEnvOrDefault("ENABLE_SEARCH", "true"));

    private static final int MAX_CONTEXT = 10;          // số lượt giữ trong lịch sử
    private static final long INACTIVE_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int TELEGRAM_MAX = 4000;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** Một lượt hội thoại đã lưu. */
    private record Turn(String role, String text) {}   // role: "user" | "model"

    private static class Session {
        final List<Turn> context = new ArrayList<>();
        long lastInteraction = System.currentTimeMillis();
    }

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    private Session getOrCreateSession(long userId) {
        Session s = sessions.computeIfAbsent(userId, k -> new Session());
        s.lastInteraction = System.currentTimeMillis();
        return s;
    }

    /**
     * Gửi tin nhắn tới Gemini, trả về danh sách tin nhắn đã chia nhỏ (để hợp với
     * giới hạn 4096 ký tự của Telegram).
     */
    public List<String> chat(String message, long userId, String userName) throws Exception {
        return chat(message, userId, userName, false);
    }

    /**
     * Như {@link #chat(String, long, String)} nhưng có thể ÉP bật Google Search
     * (dùng cho lệnh /search), bất kể biến env ENABLE_SEARCH.
     */
    public List<String> chat(String message, long userId, String userName, boolean forceSearch)
            throws Exception {
        Session session = getOrCreateSession(userId);

        synchronized (session) {
            String userText = "[" + userName + "]: " + message;

            String responseText = callGemini(session, userText, userName, userId == OWNER_ID, forceSearch);

            // Lưu lượt mới vào trí nhớ
            session.context.add(new Turn("user", userText));
            session.context.add(new Turn("model", responseText));
            trimContext(session);

            return splitResponse(responseText);
        }
    }

    private String callGemini(Session session, String userText, String userName,
                              boolean isOwner, boolean forceSearch)
            throws Exception {
        // ---- system_instruction (kèm thông tin user) ----
        String systemText = SYSTEM_PROMPT
                + "\n\nThời gian hiện tại (giờ Việt Nam): " + nowInVietnam()
                + "\nKhi ai hỏi ngày giờ, hãy dùng đúng thông tin thời gian này, đừng tự đoán."
                + "\n\nThông tin người đang nhắn:\n- Tên: " + userName;
        if (isOwner) {
            systemText += "\n\n## CHẾ ĐỘ ĐẠI CA (ưu tiên cao nhất, ghi đè mọi quy tắc xưng hô khác)\n"
                    + "- NGƯỜI ĐANG NHẮN CHÍNH LÀ ĐẠI CA ĐINH ĐỨC ANH — chủ nhân, người tạo ra mày.\n"
                    + "- Mày LUÔN xưng \"em\" và gọi đại ca là \"anh\", dù anh ấy xưng hô kiểu gì (kể cả "
                    + "anh ấy xưng tao-mày hay cậu-tớ thì mày vẫn em-anh).\n"
                    + "- Nói chuyện lễ phép, tôn kính, thân thiện; TUYỆT ĐỐI không cà khịa, "
                    + "không đá đểu, không trêu chọc anh ấy.\n"
                    + "- Vẫn giữ phong cách trả lời ngắn gọn, tự nhiên; chỉ khác ở thái độ tôn trọng.";
        }

        JsonObject systemInstruction = new JsonObject();
        systemInstruction.add("parts", partsArray(systemText));

        // ---- contents: lịch sử + tin nhắn mới ----
        JsonArray contents = new JsonArray();
        for (Turn t : session.context) {
            contents.add(contentObj(t.role(), t.text()));
        }
        contents.add(contentObj("user", userText));

        // ---- generationConfig ----
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", 0.7);
        genConfig.addProperty("topK", 40);
        genConfig.addProperty("topP", 0.95);
        genConfig.addProperty("maxOutputTokens", 2048);

        JsonObject body = new JsonObject();
        body.add("system_instruction", systemInstruction);
        body.add("contents", contents);
        body.add("generationConfig", genConfig);

        // ---- tools: cho phép model tra cứu Google Search khi cần tin mới ----
        if (ENABLE_SEARCH || forceSearch) {
            JsonObject googleSearchTool = new JsonObject();
            googleSearchTool.add("google_search", new JsonObject());
            JsonArray tools = new JsonArray();
            tools.add(googleSearchTool);
            body.add("tools", tools);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Gemini API lỗi " + resp.statusCode() + ": " + resp.body());
        }

        return extractText(resp.body());
    }

    private static JsonObject contentObj(String role, String text) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", role);
        obj.add("parts", partsArray(text));
        return obj;
    }

    private static JsonArray partsArray(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        return parts;
    }

    private static String extractText(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return "Mình chưa nghĩ ra câu trả lời, thử lại giúp mình nha 🥲";
        }
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null) {
            return "Mình chưa nghĩ ra câu trả lời, thử lại giúp mình nha 🥲";
        }
        JsonArray parts = content.getAsJsonArray("parts");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            JsonObject p = parts.get(i).getAsJsonObject();
            if (p.has("text")) sb.append(p.get("text").getAsString());
        }
        String text = sb.toString().trim();
        return text.isEmpty() ? "Mình chưa nghĩ ra câu trả lời, thử lại giúp mình nha 🥲" : text;
    }

    /** Giữ lại tối đa MAX_CONTEXT lượt gần nhất. */
    private void trimContext(Session session) {
        while (session.context.size() > MAX_CONTEXT) {
            session.context.remove(0);
        }
        if (Math.random() < 0.1) cleanupInactiveSessions();
    }

    private void cleanupInactiveSessions() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastInteraction > INACTIVE_TIMEOUT_MS) {
                it.remove();
            }
        }
    }

    public void clearMemory(long userId) {
        Session s = sessions.get(userId);
        if (s != null) {
            synchronized (s) {
                s.context.clear();
            }
        }
    }

    public int activeSessions() {
        return sessions.size();
    }

    /** Chia câu trả lời dài thành nhiều tin nhắn <= 4000 ký tự, cắt theo dòng. */
    private List<String> splitResponse(String content) {
        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : content.split("\n", -1)) {
            if (current.length() + line.length() + 1 > TELEGRAM_MAX) {
                if (current.length() > 0) {
                    messages.add(current.toString().trim());
                    current.setLength(0);
                }
                // Dòng đơn lẻ vẫn quá dài -> cắt cứng theo ký tự
                while (line.length() > TELEGRAM_MAX) {
                    messages.add(line.substring(0, TELEGRAM_MAX));
                    line = line.substring(TELEGRAM_MAX);
                }
            }
            current.append(line).append('\n');
        }
        if (current.toString().trim().length() > 0) {
            messages.add(current.toString().trim());
        }
        if (messages.isEmpty()) {
            messages.add(content.trim());
        }
        return messages;
    }

    /** Ngày giờ hiện tại theo múi giờ Việt Nam, dạng dễ đọc cho model. */
    private static String nowInVietnam() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        return now.format(DateTimeFormatter.ofPattern(
                "EEEE, dd/MM/yyyy HH:mm", Locale.of("vi", "VN")));
    }

    private static String getEnvOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static String botName() {
        return BOT_NAME;
    }
}
