import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FoodDeliveryTools {

    private final String MAPS_API_KEY;
    
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper(); // 新增：Jackson 解析器

    public FoodDeliveryTools() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.MAPS_API_KEY = dotenv.get("MAPS_API_KEY");
    }
    @Tool("Search for a list of restaurants based on user query and location.")
    public String searchRestaurants(
            @P("The food type, e.g., 'milk tea'") String query,
            @P("The city or area, e.g., 'San Francisco'") String location
    ) {
        String finalQuery = (query == null || query.isEmpty()) ? "best rated food" : query;
        String finalLocation = (location == null || location.isEmpty()) ? "Walnut Creek" : location;

        System.out.println("[TOOL CALLED] Searching list for: " + finalQuery + " in " + finalLocation);

        String url = "https://places.googleapis.com/v1/places:searchText";
        String jsonBody = "{\"textQuery\": \"" + finalQuery + " in " + finalLocation + "\"}";
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("X-Goog-Api-Key", MAPS_API_KEY)
                // 多要几个字段，但我们自己解析，不让 Gemini 看原始 JSON
                .addHeader("X-Goog-FieldMask", "places.displayName,places.rating,places.userRatingCount,places.formattedAddress,places.priceLevel")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return "Error: " + response.code();
            String rawJson = response.body().string();
            return parseRestaurantsToText(rawJson); // 解析成干净文本
        } catch (IOException e) {
            return "Connection Error: " + e.getMessage();
        }
    }

    // 新增：把 Google 的大 JSON 裁剪成 Gemini 友好的纯文本
    private String parseRestaurantsToText(String rawJson) {
        try {
            JsonNode root = mapper.readTree(rawJson);
            JsonNode places = root.get("places");

            if (places == null || places.isEmpty()) {
                return "No restaurants found.";
            }

            StringBuilder sb = new StringBuilder("Here are the top results:\n");
            int i = 1;
            for (JsonNode place : places) {
                String name = place.path("displayName").path("text").asText("Unknown");
                double rating = place.path("rating").asDouble(0);
                int reviewCount = place.path("userRatingCount").asInt(0);
                String address = place.path("formattedAddress").asText("No address");
                String priceRaw = place.path("priceLevel").asText("");

                // 把 PRICE_LEVEL_MODERATE 转成 $$
                String price = switch (priceRaw) {
                    case "PRICE_LEVEL_INEXPENSIVE" -> "$";
                    case "PRICE_LEVEL_MODERATE" -> "$$";
                    case "PRICE_LEVEL_EXPENSIVE" -> "$$$";
                    case "PRICE_LEVEL_VERY_EXPENSIVE" -> "$$$$";
                    default -> "N/A";
                };

                sb.append(String.format("%d. %s | ⭐%.1f (%d reviews) | %s | %s\n",
                        i++, name, rating, reviewCount, price, address));
            }

            return sb.toString();

        } catch (Exception e) {
            // 解析失败就返回原始 JSON，至少不报错
            System.err.println("[WARN] Failed to parse restaurants JSON: " + e.getMessage());
            return rawJson;
        }
    }

    // getRestaurantReviews 暂时不动，reviews 本来就需要原文给 Gemini 读
    @Tool("Get detailed customer reviews for a SPECIFIC restaurant.")
    public String getRestaurantReviews(
            @P("The specific name of the restaurant") String restaurantName,
            @P("The city or area") String location
    ) {
        String finalName = (restaurantName == null) ? "" : restaurantName;
        String finalLoc = (location == null) ? "Walnut Creek" : location;

        System.out.println("[TOOL CALLED] Fetching REVIEWS for: " + finalName + " in " + finalLoc);

        String url = "https://places.googleapis.com/v1/places:searchText";
        String jsonBody = "{\"textQuery\": \"" + finalName + " in " + finalLoc + "\", \"pageSize\": 1}";
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("X-Goog-Api-Key", MAPS_API_KEY)
                .addHeader("X-Goog-FieldMask", "places.displayName,places.reviews")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return "Error: " + response.code();
            return response.body().string();
        } catch (IOException e) {
            return "Connection Error: " + e.getMessage();
        }
    }
}