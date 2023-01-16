import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.*;
import okhttp3.*;

public class TranslatorText {
    private static String key = "9a7b89f2526247049ab6ec3980ae56a8";

    // location, also known as region.
    // required if you're using a multi-service or regional (not global) resource. It can be found in the Azure portal on the Keys and Endpoint page.
    private static String location = "germanywestcentral";


    // Instantiates the OkHttpClient.
    OkHttpClient client = new OkHttpClient();

    // This function performs a POST request.
    public ArrayList<String> post(String word) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType,
                "[{\"Text\": \"" + word + "\"}]");
        Request request = new Request.Builder()
                .url("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=en&to=ru")
                .post(body)
                .addHeader("Ocp-Apim-Subscription-Key", key)
                // location required if you're using a multi-service or regional (not global) resource.
                .addHeader("Ocp-Apim-Subscription-Region", location)
                .addHeader("Content-type", "application/json")
                .build();
        Response response = client.newCall(request).execute();
        String jsonString = response.body().string();

        ArrayList<String> result = new ArrayList<>();
        try {
            JsonParser jsonParser = new JsonParser();
            JsonArray jsonArray = jsonParser.parse(jsonString).getAsJsonArray();
            JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();

            JsonArray translations = jsonObject.get("translations").getAsJsonArray();
            for (JsonElement translation : translations) {
                JsonObject translationObject = translation.getAsJsonObject();
                result.add(translationObject.get("text").getAsString());
            }

        } catch (Exception e) {
            System.out.println(e);
        }

        return result;
    }
}