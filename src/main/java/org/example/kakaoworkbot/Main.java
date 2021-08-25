package org.example.kakaoworkbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.example.kakaoworkbot.models.User;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {
    private static String appKey;
    private static User[] users;

    public static void main(String[] args) {
        appKey = System.getenv("APP_KEY");
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(new URI("https://api.kakaowork.com/v1/users.list"))
                    .GET()
                    .header("Authorization", "Bearer " + appKey)
                    .build();
            String response = HttpClient.newBuilder()
                    .build()
                    .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .join()
                    .body();
            JsonElement jsonElement = JsonParser.parseString(response);
            boolean success = jsonElement.getAsJsonObject()
                    .get("success")
                    .getAsBoolean();
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            if (!success) {
                System.out.println(gson.toJson(jsonElement.getAsJsonObject()
                        .get("error")));
                return;
            }
            users = gson.fromJson(jsonElement.getAsJsonObject().get("users"), User[].class);
            for (User user : users) {
                System.out.println(user.id);
            }
            System.out.println(gson.toJson(jsonElement));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
