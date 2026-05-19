import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class NutritionTools {

    private static final String USDA_API_KEY = "HvpxoMgztANAa0pVlN2fNbeXNptSgfLWrB4aqof7";
    private static final String USDA_BASE_URL = "https://api.nal.usda.gov/fdc/v1";
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // 用于用户直接文字询问热量的情况，e.g. "how many calories in ramen?"
    @Tool({
            "Use this tool when the user asks about calories or nutrition of a specific food BY NAME.",
            "Do NOT use this for image analysis - images are handled separately.",
            "Input the English name of the food, e.g., 'sushi', 'ramen', 'fried chicken'."
    })
    public String getNutrition(
            @P("The English name of the food, e.g., 'sushi'") String foodName
    ) {
        System.out.println("[NUTRITION TOOL] Looking up: " + foodName);

        try {
            String encoded = URLEncoder.encode(foodName, StandardCharsets.UTF_8);
            String searchUrl = USDA_BASE_URL + "/foods/search?query=" + encoded
                    + "&pageSize=1&api_key=" + USDA_API_KEY;

            Request searchRequest = new Request.Builder().url(searchUrl).get().build();

            try (Response searchResponse = client.newCall(searchRequest).execute()) {
                if (!searchResponse.isSuccessful()) return "USDA API error: " + searchResponse.code();

                JsonNode root = mapper.readTree(searchResponse.body().string());
                JsonNode foods = root.get("foods");

                if (foods == null || foods.isEmpty()) {
                    return "No nutrition data found for: " + foodName;
                }

                JsonNode food = foods.get(0);
                String foundName = food.path("description").asText("Unknown");
                JsonNode nutrients = food.get("foodNutrients");

                if (nutrients == null) return "No nutrient data available.";

                double calories = 0, protein = 0, carbs = 0, fat = 0, fiber = 0, sodium = 0;

                for (JsonNode n : nutrients) {
                    String name = n.path("nutrientName").asText("");
                    double value = n.path("value").asDouble(0);

                    if (name.contains("Energy") && name.contains("KCAL") || name.equals("Energy")) calories = value;
                    else if (name.equals("Protein")) protein = value;
                    else if (name.contains("Carbohydrate")) carbs = value;
                    else if (name.contains("Total lipid")) fat = value;
                    else if (name.contains("Fiber")) fiber = value;
                    else if (name.contains("Sodium")) sodium = value;
                }

                String result = String.format("""
                    Nutrition info for: %s (per 100g)
                    🔥 Calories: %.0f kcal
                    💪 Protein: %.1f g
                    🍞 Carbs: %.1f g
                    🧈 Fat: %.1f g
                    🥦 Fiber: %.1f g
                    🧂 Sodium: %.0f mg
                    """, foundName, calories, protein, carbs, fat, fiber, sodium);

                System.out.println("[NUTRITION RESULT]\n" + result);
                return result;
            }

        } catch (IOException e) {
            return "Connection error: " + e.getMessage();
        }
    }
}