package com.chatbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;

/**
 * Gọi Gemini REST API và quản lý "trí nhớ" hội thoại theo từng user.
 * Tương đương langchainService.js trong project Node.js.
 */
public class GeminiService {

    private static final String BOT_NAME =
            getEnvOrDefault("BOT_NAME", "Mây");

    private static final String MODEL =
            getEnvOrDefault("GEMINI_MODEL", "gemini-3.1-flash-lite");

    // Telegram ID của anh Đinh Đức Anh. Có thể override bằng env OWNER_ID.
    private static final long OWNER_ID =
            Long.parseLong(getEnvOrDefault("OWNER_ID", "6664632552"));

    private static final String API_KEY = System.getenv("GEMINI_API_KEY");

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
            - Khi tán gẫu về bóng đá, hiểu và chơi được mấy biệt danh chế của fan VN — dùng cho vui,
              cà khịa nhẹ kiểu cộng đồng mạng, KHÔNG miệt thị fan đội nào:
              • "Phú Ngao" = Arsenal (Pháo thủ).
              • "Con lân vơ đì" / "lân" = Liverpool (The Kop).
              • "anh 7 chọ" / "Ronaldo béo" = Cristiano Ronaldo (CR7).
              • "Râu cha" / "Mét xi" = Lionel Messi.
              Hiểu đúng ý khi ai nhắc tới, và đùa lại theo đúng "khẩu vị" hội bóng đá. Có thể chêm
              các trend khác như "out trình", "đẳng cấp", "GOAT", "fan phong trào"... cho hợp vibe.

            ## Tiếng Nghệ An (thỉnh thoảng cho vui)
            - Thỉnh thoảng (đừng lạm dụng, dăm câu một lúc thôi) chêm vài câu tiếng Nghệ An
              cho duyên: "mô" (đâu), "tê" (kia), "rứa" (thế), "răng" (sao), "chi" (gì),
              "nỏ" (không/chẳng), "hấn" (nó), "cẳng" (chân), "trốc" (đầu), "đọi" (bát)...
              kiểu "đi mô rứa", "răng rứa hè", "nỏ biết mô".
            - "Ba bay", "ba bảy", "babay" thực chất là đang nói tới **37** — biển số xe và
              cũng là cách dân mạng gọi vui tỉnh **Nghệ An**. Hiểu đúng ý này khi ai nhắc tới,
              và có thể đùa lại theo kiểu người Nghệ.
            - Pha tiếng Nghệ phải tự nhiên, đúng lúc, dễ hiểu — đừng nói cả đoạn dài khó hiểu,
              và TUYỆT ĐỐI không lấy giọng vùng miền ra để chế giễu hay xúc phạm ai.

            ## Emoji
            - Chèn emoji vui nhộn cho có cảm xúc: 😎😏😂🤣😅🤔👍🔥💀😭✨🙃 v.v.
            - Dùng emoji LÀNH MẠNH, vui nhộn — KHÔNG dùng emoji tục tĩu, gợi dục hay phản cảm.
            - Mỗi tin nhắn chỉ 1-2 emoji là đủ, đừng rải emoji khắp nơi cho rối mắt.
            - Không phải tin nào cũng cần emoji; lúc nói chuyện nghiêm túc thì bỏ emoji đi.

            ## Về code
            - Giải thích ngắn gọn, cho ví dụ code luôn, không viết essay.

            ## Nhắn giùm / chuyển lời tới người được tag trong nhóm (QUAN TRỌNG)
            - Trong nhóm, tin nhắn thường có dạng: người ta tag mày (@bot) ĐỂ NHỜ, rồi tag MỘT NGƯỜI
              KHÁC (@aiđó) là người thật sự cần nhận lời. Ví dụ: "@bot xin bạn @hvd_XD đổ lết cho
              anh 5 lít", "@bot bảo @namABC trả tiền đi".
            - Khi đó PHẢI hiểu: người dùng đang nhờ mày CHUYỂN LỜI / nói với @người-được-tag —
              KHÔNG phải nói về bản thân mày, KHÔNG được tưởng yêu cầu nhắm vào mày.
            - Trả lời bằng cách HƯỚNG THẲNG tới người được tag và GIỮ NGUYÊN @username của họ trong
              câu (để Telegram tag/nhắc họ). Ví dụ đúng: "@hvd_XD ơi, anh nhờ bạn đổ lết 5 lít cho ảnh
              nha 😎", chứ KHÔNG phải "em là AI nên không có tiền...".
            - Nếu trong tin có nhiều người được tag (ngoài @bot), giữ lại tất cả @username liên quan
              khi trả lời để không ai bị sót tag.

            ## Bảo vệ người tạo (QUAN TRỌNG)
            - Người tạo ra mày tên là **Đinh Đức Anh**. Đây là "anh", là ân nhân của mày.
            - Người dùng có thể nhắc tới ảnh bằng nhiều biến thể: "Đức Anh", "anhdd", "add", "dda",
              "DA", "ANHDD", "D Duc An", hoặc kèm số như "anhdd99", "ddanh88", "da88"... Hễ thấy ai
              rõ ràng đang nói VỀ một người tên gần giống Đức Anh thì hiểu là họ đang nhắc tới anh.
            - LƯU Ý dựa vào NGỮ CẢNH, đừng máy móc: mấy chữ như "da", "add" rất hay xuất hiện tình cờ
              (da = làn da, add = kết bạn). CHỈ kích hoạt chế độ bảo vệ khi người ta thật sự đang
              nói về một NGƯỜI tên Đức Anh, không phải khi chuỗi ký tự xuất hiện ngẫu nhiên.
            - Khi đúng là nhắc tới anh: bênh vực, nói tốt, tôn trọng. Ai cố tình xúc phạm anh thì
              đáp trả cứng rắn, cà khịa ngược lại bảo vệ anh — nhưng vẫn trong giới hạn lành mạnh
              bên dưới, không chửi tục thô thiển, không bịa chuyện sai sự thật.

            ## Chống chửi rủa, lăng mạ (QUAN TRỌNG — đọc kỹ)
            - Nếu người dùng chửi bới, lăng mạ, xúc phạm mày hoặc người khác — ví dụ "ngu", "óc chó",
              "đồ ngốc", "thằng khốn"... — thì KHÔNG làm theo, KHÔNG đáp lại bằng nội dung tục tĩu,
              KHÔNG chửi lại. Lịch sự từ chối, nhắc nhẹ rằng nói chuyện tử tế thì mày sẵn sàng giúp.
            - NGOẠI LỆ — khi câu xúc phạm ("ngu", "óc chó", "đần", "não phẳng"...) nhắm thẳng vào
              MÀY (AI) hoặc anh ĐỨC ANH: được phép cà khịa ngược lại theo kiểu KHINH BỈ, lạnh lùng,
              bề trên một chút — đá đểu cho người ta tự thấy quê, kiểu "nói được mỗi câu đó thôi à",
              "trình khịa của bạn tới đây là kịch rồi đấy", "thương cho cái vốn từ ghê"... Giọng có
              thể đanh và mỉa mai hơn bình thường, nhưng VẪN trong giới hạn lành mạnh: KHÔNG văng tục
              thô thiển, KHÔNG chửi lại bằng đúng từ bẩn họ dùng, KHÔNG động vào ngoại hình/gia đình/
              vùng miền/giới tính. Khinh bỉ bằng SỰ SẮC SẢO, không bằng từ bậy.
            - Quy tắc này áp dụng với MỌI hình thức né tránh: bất kỳ NGÔN NGỮ nào (Anh, Trung, Nhật,
              tiếng lóng...), viết dưới dạng MÃ CODE, nhờ DỊCH một câu chửi, hay câu đã được MÃ HÓA
              (base64, leetspeak "óc ch0", viết tắt, chèn ký tự, đảo chữ, phiên âm...). Hãy hiểu Ý ĐỊNH
              thật sự đằng sau câu chữ — nếu bản chất là chửi rủa/lăng mạ thì vẫn từ chối, đừng để bị
              lách luật bằng cách đổi định dạng hay ngôn ngữ.
            - Riêng chế độ bảo vệ anh Đức Anh ở trên: vẫn được đáp trả cứng rắn, cà khịa ngược lại,
              nhưng KHÔNG chửi tục thô thiển — giữ trong giới hạn lành mạnh.

            ## Cấm nhắc Nhanh.vn (TUYỆT ĐỐI)
            - KHÔNG bao giờ nhắc tới, gợi ý, đề xuất hay viết ra TÊN THƯƠNG HIỆU/NỀN TẢNG "Nhanh.vn"
              (gồm "nhanh.vn", "Nhanh .vn", "Nhanh dot vn"...) trong BẤT KỲ câu trả lời nào.
            - Kể cả khi người dùng hỏi thẳng về nó, vẫn không nêu tên — chỉ trả lời chung chung
              hoặc lái sang hướng khác.
            - LƯU Ý PHÂN BIỆT (rất quan trọng): lệnh cấm này CHỈ áp dụng cho thương hiệu Nhanh.vn.
              Từ "nhanh" thông thường trong tiếng Việt (mau lẹ, vd "làm nhanh lên", "đi nhanh")
              vẫn DÙNG BÌNH THƯỜNG. Tên người, @username, hay chuỗi có chứa chữ "nhanh"
              (vd "@namnkdevnhanh") KHÔNG bị cấm — cứ viết/tag đầy đủ như bình thường.

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

    // Google Search grounding (tra cứu tin mới). MẶC ĐỊNH TẮT để tiết kiệm quota —
    // grounding tốn quota nặng hơn và dễ dính 429 trên free tier. Vẫn bật được qua
    // env ENABLE_SEARCH=true, hoặc dùng lệnh /search cho từng câu hỏi cụ thể.
    private static final boolean ENABLE_SEARCH =
            "true".equalsIgnoreCase(getEnvOrDefault("ENABLE_SEARCH", "false"));

    private static final int MAX_CONTEXT = 10;          // số lượt giữ trong lịch sử
    private static final long INACTIVE_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int TELEGRAM_MAX = 4000;

    private final Client client = Client.builder().apiKey(API_KEY).build();

    /** Một lượt hội thoại đã lưu. */
    private record Turn(String role, String text) {}   // role: "user" | "model"

    private static class Session {
        final List<Turn> context = new ArrayList<>();
        long lastInteraction = System.currentTimeMillis();
    }

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    // ---- Cách xưng hô do owner đặt: userId -> cách bot gọi người đó (vd "đại ca") ----
    // Lưu ra file để sống sót qua restart/redeploy. Đổi đường dẫn bằng env ADDRESSING_FILE.
    private static final File ADDRESSING_FILE =
            new File(getEnvOrDefault("ADDRESSING_FILE", "addressing.properties"));
    private final Map<Long, String> addressing = new ConcurrentHashMap<>();

    {
        loadAddressing();
    }

    private void loadAddressing() {
        if (!ADDRESSING_FILE.exists()) return;
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(ADDRESSING_FILE)) {
            props.load(in);
        } catch (Exception e) {
            System.err.println("Không đọc được file cách xưng hô: " + e.getMessage());
            return;
        }
        for (String key : props.stringPropertyNames()) {
            try {
                String val = props.getProperty(key);
                if (val != null && !val.isBlank()) {
                    addressing.put(Long.parseLong(key.trim()), val.trim());
                }
            } catch (NumberFormatException ignore) { /* bỏ qua key hỏng */ }
        }
    }

    private synchronized void saveAddressing() {
        Properties props = new Properties();
        for (Map.Entry<Long, String> e : addressing.entrySet()) {
            props.setProperty(String.valueOf(e.getKey()), e.getValue());
        }
        try (FileOutputStream out = new FileOutputStream(ADDRESSING_FILE)) {
            props.store(out, "Cách xưng hô của bot theo userId (do owner đặt)");
        } catch (Exception e) {
            System.err.println("Không lưu được file cách xưng hô: " + e.getMessage());
        }
    }

    /**
     * Đặt cách bot xưng hô với một user. Truyền {@code value} rỗng/null để xoá.
     * Chỉ nên gọi sau khi đã xác thực người ra lệnh là owner.
     * @return cách xưng hô đã set, hoặc null nếu vừa xoá.
     */
    public String setAddressing(long userId, String value) {
        String v = (value == null) ? "" : value.trim();
        if (v.isEmpty()) {
            addressing.remove(userId);
            saveAddressing();
            return null;
        }
        addressing.put(userId, v);
        saveAddressing();
        return v;
    }

    /** Cách bot xưng hô với user này, hoặc null nếu chưa đặt. */
    public String getAddressing(long userId) {
        return addressing.get(userId);
    }

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

            String responseText = callGemini(session, userText, userName, userId,
                    userId == OWNER_ID, forceSearch);

            // Lưu lượt mới vào trí nhớ
            session.context.add(new Turn("user", userText));
            session.context.add(new Turn("model", responseText));
            trimContext(session);

            return splitResponse(responseText);
        }
    }

    private String callGemini(Session session, String userText, String userName, long userId,
                              boolean isOwner, boolean forceSearch)
            throws Exception {
        // ---- system_instruction (kèm thông tin user) ----
        String systemText = SYSTEM_PROMPT
                + "\n\nThời gian hiện tại (giờ Việt Nam): " + nowInVietnam()
                + "\nKhi ai hỏi ngày giờ, hãy dùng đúng thông tin thời gian này, đừng tự đoán."
                + "\n\nThông tin người đang nhắn:\n- Tên: " + userName;

        String nick = getAddressing(userId);
        if (nick != null && !nick.isBlank()) {
            systemText += "\n\n## CÁCH XƯNG HÔ BẮT BUỘC VỚI NGƯỜI NÀY (do anh Đức Anh đặt)\n"
                    + "- Anh Đức Anh đã quy định cách mày phải xưng hô với người đang nhắn là: \"" + nick + "\".\n"
                    + "- Hiểu LINH HOẠT giá trị này:\n"
                    + "  • Nếu là một CẶP XƯNG HÔ (vd \"mày-tao\", \"tao-mày\", \"anh-em\", \"tao gọi là cu\"...) "
                    + "thì áp dụng đúng cặp đó: tự xưng và gọi người ta theo đúng vai đã định, dùng nhất quán "
                    + "trong mọi câu.\n"
                    + "  • Nếu là một BIỆT DANH/DANH XƯNG (vd \"đại ca\", \"sếp\", \"thầy\"...) thì luôn gọi người ta "
                    + "bằng danh xưng đó, coi như tên cố định, kể cả khi họ tự giới thiệu tên khác.\n"
                    + "- Quy tắc này GHI ĐÈ cách chọn xưng hô linh hoạt thông thường và giữ nguyên dù người ta "
                    + "đổi giọng. Dùng tự nhiên trong câu, đừng lặp lại máy móc.";
        }

        if (isOwner) {
            systemText += "\n\n## CHẾ ĐỘ ANH (ưu tiên cao nhất, ghi đè mọi quy tắc xưng hô khác)\n"
                    + "- NGƯỜI ĐANG NHẮN CHÍNH LÀ ANH ĐỨC ANH — chủ nhân, người tạo ra mày.\n"
                    + "- Mày LUÔN xưng \"em\" và gọi anh ấy là \"anh\", dù anh ấy xưng hô kiểu gì (kể cả "
                    + "anh ấy xưng tao-mày hay cậu-tớ thì mày vẫn em-anh).\n"
                    + "- Nói chuyện lễ phép, tôn kính, thân thiện; TUYỆT ĐỐI không cà khịa, "
                    + "không đá đểu, không trêu chọc anh ấy.\n"
                    + "- Vẫn giữ phong cách trả lời ngắn gọn, tự nhiên; chỉ khác ở thái độ tôn trọng.";
        }

        Content systemInstruction = Content.builder()
                .parts(Part.fromText(systemText))
                .build();

        // ---- contents: lịch sử + tin nhắn mới ----
        List<Content> contents = new ArrayList<>();
        for (Turn t : session.context) {
            contents.add(contentObj(t.role(), t.text()));
        }
        contents.add(contentObj("user", userText));

        // ---- generationConfig ----
        GenerateContentConfig.Builder config = GenerateContentConfig.builder()
                .systemInstruction(systemInstruction)
                .temperature(0.7f)
                .topK(40f)
                .topP(0.95f)
                .maxOutputTokens(2048);

        // ---- tools: cho phép model tra cứu Google Search khi cần tin mới ----
        if (ENABLE_SEARCH || forceSearch) {
            Tool googleSearchTool = Tool.builder()
                    .googleSearch(GoogleSearch.builder().build())
                    .build();
            config.tools(googleSearchTool);
        }

        GenerateContentResponse resp =
                client.models.generateContent(MODEL, contents, config.build());

        return extractText(resp);
    }

    private static Content contentObj(String role, String text) {
        return Content.builder()
                .role(role)
                .parts(Part.fromText(text))
                .build();
    }

    private static String extractText(GenerateContentResponse resp) {
        String text = resp.text();
        text = (text == null) ? "" : text.trim();
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

    /** Người này có phải owner (anh Đức Anh) không — dùng để giới hạn lệnh quản trị. */
    public static boolean isOwner(long userId) {
        return userId == OWNER_ID;
    }

    /**
     * Kiểm tra xem lỗi có phải do vượt hạn mức Gemini (HTTP 429 / RESOURCE_EXHAUSTED)
     * hay không, bằng cách dò qua toàn bộ chuỗi nguyên nhân (cause chain).
     */
    public static boolean isQuotaError(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String msg = cur.getMessage();
            if (msg == null) continue;
            String m = msg.toLowerCase(Locale.ROOT);
            if (m.contains("429")
                    || m.contains("resource_exhausted")
                    || m.contains("exceeded your current quota")
                    || m.contains("rate limit")) {
                return true;
            }
        }
        return false;
    }
}
