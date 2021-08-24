package org.example.kakaoworkbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) {
        try {
            String appKey = System.getenv("APP_KEY");
            System.out.println(appKey);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(new URI("https://api.kakaowork.com/v1/users.list"))
                    .GET()
                    .header("Authorization", "Bearer " + appKey)
                    .build();
            String response = HttpClient.newBuilder()
                    .build()
                    .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .get()
                    .body();
            JsonElement jsonElement = JsonParser.parseString(response);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(jsonElement));
        } catch (URISyntaxException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
