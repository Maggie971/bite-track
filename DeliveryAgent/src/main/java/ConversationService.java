import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

public class ConversationService {

    private final MongoCollection<Document> conversationsCollection;

    public ConversationService(MongoCollection<Document> conversationsCollection) {
        this.conversationsCollection = conversationsCollection;
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // ─────────────────────────────────────────────
    // 存一条消息，如果 session 不存在就自动创建
    // ─────────────────────────────────────────────
    public void saveMessage(String userId, String sessionId, String role, String text, String timestamp) {
        if (userId == null || userId.equals("guest") || text == null || text.isBlank()) return;

        String ts = (timestamp != null && !timestamp.isBlank()) ? timestamp : now();

        // 如果 session 不存在，创建一个，标题先用占位符，后面 updateTitle 会更新
        Document existing = conversationsCollection.find(Filters.eq("sessionId", sessionId)).first();
        if (existing == null) {
            Document session = new Document()
                    .append("sessionId", sessionId)
                    .append("userId", userId)
                    .append("title", "New conversation")
                    .append("createdAt", ts)
                    .append("updatedAt", ts)
                    .append("messages", new ArrayList<>());
            conversationsCollection.insertOne(session);
        }

        // push 消息进数组
        conversationsCollection.updateOne(
                Filters.eq("sessionId", sessionId),
                new Document("$push", new Document("messages", new Document()
                        .append("role", role)
                        .append("text", text)
                        .append("timestamp", ts)
                ))
        );

        // 更新 updatedAt
        conversationsCollection.updateOne(
                Filters.eq("sessionId", sessionId),
                new Document("$set", new Document("updatedAt", ts))
        );

        System.out.println("[CONV] Saved [" + role + "] to session: " + sessionId);
    }

    // ─────────────────────────────────────────────
    // 更新会话标题（由 Gemini 生成后调用）
    // ─────────────────────────────────────────────
    public void updateTitle(String sessionId, String title) {
        if (title == null || title.isBlank()) return;
        conversationsCollection.updateOne(
                Filters.eq("sessionId", sessionId),
                new Document("$set", new Document("title", title))
        );
        System.out.println("[CONV] Updated title for " + sessionId + ": " + title);
    }

    // ─────────────────────────────────────────────
    // 查询某个 session 是否已经有标题（避免重复生成）
    // ─────────────────────────────────────────────
    public boolean hasCustomTitle(String sessionId) {
        Document doc = conversationsCollection.find(Filters.eq("sessionId", sessionId)).first();
        if (doc == null) return false;
        String title = doc.getString("title");
        return title != null && !title.equals("New conversation");
    }

    // ─────────────────────────────────────────────
    // 获取用户所有会话列表（只返回元数据，不返回消息内容）
    // ─────────────────────────────────────────────
    public List<Map<String, String>> getSessionList(String userId, int limit) {
        var sessions = conversationsCollection.find(
                Filters.eq("userId", userId)
        ).sort(Sorts.descending("updatedAt")).limit(limit);

        List<Map<String, String>> results = new ArrayList<>();
        for (Document doc : sessions) {
            results.add(Map.of(
                    "sessionId", doc.getString("sessionId") != null ? doc.getString("sessionId") : "",
                    "title", doc.getString("title") != null ? doc.getString("title") : "Conversation",
                    "updatedAt", doc.getString("updatedAt") != null ? doc.getString("updatedAt") : ""
            ));
        }
        return results;
    }

    // ─────────────────────────────────────────────
    // 获取某次会话的完整消息
    // ─────────────────────────────────────────────
    public List<Map<String, String>> getMessages(String sessionId) {
        Document doc = conversationsCollection.find(Filters.eq("sessionId", sessionId)).first();
        if (doc == null) return List.of();

        List<Document> messages = doc.getList("messages", Document.class);
        List<Map<String, String>> result = new ArrayList<>();
        if (messages != null) {
            for (Document msg : messages) {
                result.add(Map.of(
                        "role", msg.getString("role") != null ? msg.getString("role") : "",
                        "text", msg.getString("text") != null ? msg.getString("text") : "",
                        "timestamp", msg.getString("timestamp") != null ? msg.getString("timestamp") : ""
                ));
            }
        }
        return result;
    }
}