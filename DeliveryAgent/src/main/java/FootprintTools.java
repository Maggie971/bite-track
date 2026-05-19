import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class FootprintTools {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final MongoCollection<Document> historyCollection;

    public FootprintTools(EmbeddingStore<TextSegment> embeddingStore,
                          EmbeddingModel embeddingModel,
                          MongoDatabase mongoDatabase) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        // ✅ 普通 MongoDB 集合，精确查询用
        this.historyCollection = mongoDatabase.getCollection("user_history");
    }

    // ─────────────────────────────────────────────
    // 6点窗口：当天06:00 到次日05:59 算同一个饮食日
    // 例：凌晨2点 → 属于昨天的饮食日
    // ─────────────────────────────────────────────
    private String getDietDay() {
        LocalDateTime now = LocalDateTime.now();
        if (now.toLocalTime().isBefore(LocalTime.of(6, 0))) {
            return now.toLocalDate().minusDays(1).toString();
        }
        return now.toLocalDate().toString();
    }

    private String getNowTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    // ─────────────────────────────────────────────
    // Tool 1: 记录搜索/浏览足迹 → 普通 MongoDB
    // ─────────────────────────────────────────────
    @Tool({
            "Use this tool to record what the user searched for or which restaurant they viewed.",
            "Call this whenever the user mentions searching for food or asks about a specific restaurant.",
            "This builds a browsing history for better future recommendations."
    })
    public void saveFootprint(
            @P("The user's ID") String userId,
            @P("Type: 'search' or 'view'") String type,
            @P("Description, e.g., 'Searched sushi in Walnut Creek' or 'Viewed Tanoshi Japanese Bistro'") String content
    ) {
        String dietDay = getDietDay();
        Document doc = new Document()
                .append("userId", userId)
                .append("type", type)
                .append("content", content)
                .append("calories", null)
                .append("dietDay", dietDay)
                .append("timestamp", getNowTimestamp());

        historyCollection.insertOne(doc);
        System.out.println("👣 [FOOTPRINT SAVED] [" + type + "] " + content);
    }

    // ─────────────────────────────────────────────
    // Tool 2: 记录热量 → 普通 MongoDB
    // 图片分析后由 Main.java 自动调用，或用户说"我吃了XX"时调用
    // ─────────────────────────────────────────────
    @Tool({
            "Use this tool to record calories consumed by the user.",
            "Call this when the user tells you what they ate, e.g., 'I just had a burger' or 'I ate ramen for lunch'.",
            "For image analysis, calories are recorded automatically - do NOT call this for image uploads.",
            "Estimate calories reasonably if the user doesn't specify."
    })
    public void saveCalorieRecord(
            @P("The user's ID") String userId,
            @P("Description of what was eaten, e.g., 'Ramen with chashu and egg'") String foodDescription,
            @P("Estimated calories as integer, e.g., 650") int calories
    ) {
        String dietDay = getDietDay();
        Document doc = new Document()
                .append("userId", userId)
                .append("type", "calorie")
                .append("content", foodDescription)
                .append("calories", calories)
                .append("dietDay", dietDay)
                .append("timestamp", getNowTimestamp());

        historyCollection.insertOne(doc);
        System.out.println("🍽️ [CALORIE SAVED] " + foodDescription + " → " + calories + " kcal (DietDay: " + dietDay + ")");
    }

    // ─────────────────────────────────────────────
    // Tool 3: 设置每日热量目标
    // 用户说"我今天目标是1800卡"时调用
    // ─────────────────────────────────────────────
    @Tool({
            "Use this tool when the user sets or updates their daily calorie goal.",
            "Example triggers: 'my goal is 1800 calories', 'I want to eat 1500 kcal today', 'set my calorie target to 2200'.",
            "This goal is used to calculate remaining calories for the day."
    })
    public void saveCalorieGoal(
            @P("The user's ID") String userId,
            @P("Daily calorie goal as integer, e.g., 1800") int goalCalories
    ) {
        String dietDay = getDietDay();
        // 先删除今天已有的 goal，再插入新的（同一天只保留最新目标）
        historyCollection.deleteMany(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", "goal"),
                Filters.eq("dietDay", dietDay)
        ));

        Document doc = new Document()
                .append("userId", userId)
                .append("type", "goal")
                .append("content", "Daily calorie goal: " + goalCalories + " kcal")
                .append("calories", goalCalories)
                .append("dietDay", dietDay)
                .append("timestamp", getNowTimestamp());

        historyCollection.insertOne(doc);
        System.out.println("🎯 [GOAL SAVED] " + userId + " → " + goalCalories + " kcal/day (DietDay: " + dietDay + ")");
    }

    // ─────────────────────────────────────────────
    // Tool 4: 查询今日热量摄入 + 剩余预算
    // 直接 MongoDB 精确查询，不靠向量检索
    // ─────────────────────────────────────────────
    @Tool({
            "Use this tool to get the user's calorie summary for today.",
            "Call this when user asks 'how many calories today', 'what's my calorie count', or 'how much can I still eat'.",
            "Also call this before making meal recommendations to know remaining calorie budget."
    })
    public String getTodayCalories(
            @P("The user's ID") String userId
    ) {
        String dietDay = getDietDay();
        System.out.println("📊 [CALORIE QUERY] userId=" + userId + " dietDay=" + dietDay);

        // 1. 查今天的热量目标
        Document goalDoc = historyCollection.find(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", "goal"),
                Filters.eq("dietDay", dietDay)
        )).sort(Sorts.descending("timestamp")).first();

        int dailyGoal = (goalDoc != null) ? goalDoc.getInteger("calories", 2000) : 2000;
        String goalNote = (goalDoc != null) ? "" : " (default, you can set your own goal by telling me!)";

        // 2. 查今天所有热量记录并加总
        var records = historyCollection.find(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", "calorie"),
                Filters.eq("dietDay", dietDay)
        )).sort(Sorts.ascending("timestamp"));

        int totalCalories = 0;
        StringBuilder breakdown = new StringBuilder();
        for (Document record : records) {
            int cal = record.getInteger("calories", 0);
            String content = record.getString("content");
            String time = record.getString("timestamp");
            // 只显示时间部分 HH:mm
            String timeShort = time != null && time.length() >= 16 ? time.substring(11, 16) : "";
            totalCalories += cal;
            breakdown.append("  • [").append(timeShort).append("] ")
                    .append(content).append(": ").append(cal).append(" kcal\n");
        }

        int remaining = dailyGoal - totalCalories;
        String status;
        if (totalCalories == 0) {
            status = "No meals logged yet today!";
        } else if (remaining > 0) {
            status = "✅ " + remaining + " kcal remaining today.";
        } else {
            status = "⚠️ Exceeded daily goal by " + Math.abs(remaining) + " kcal!";
        }

        String result = String.format(
                "📊 Calorie Summary (DietDay: %s)\n%s\n🔥 Total: %d / %d kcal%s\n%s",
                dietDay,
                breakdown.length() > 0 ? breakdown.toString() : "  (no meals logged)\n",
                totalCalories, dailyGoal, goalNote,
                status
        );

        System.out.println("[CALORIE RESULT]\n" + result);
        return result;
    }

    // ─────────────────────────────────────────────
    // Tool 5: 获取最近浏览/搜索历史（用于个性化推荐）
    // ─────────────────────────────────────────────
    @Tool({
            "Use this tool to get the user's recent search and restaurant browsing history.",
            "Call this when making personalized recommendations to avoid suggesting places they've already visited.",
            "Also useful when user asks 'what have I been searching for lately'."
    })
    public String getRecentHistory(
            @P("The user's ID") String userId
    ) {
        System.out.println("🔍 [HISTORY QUERY] userId=" + userId);

        var records = historyCollection.find(Filters.and(
                Filters.eq("userId", userId),
                Filters.in("type", "search", "view")
        )).sort(Sorts.descending("timestamp")).limit(10);

        StringBuilder history = new StringBuilder("Recent activity:\n");
        int count = 0;
        for (Document record : records) {
            String type = record.getString("type");
            String content = record.getString("content");
            String dietDay = record.getString("dietDay");
            history.append("  • [").append(dietDay).append("] [").append(type).append("] ").append(content).append("\n");
            count++;
        }

        if (count == 0) return "No browsing history found for this user.";

        System.out.println("[HISTORY RESULT]\n" + history);
        return history.toString();
    }
}