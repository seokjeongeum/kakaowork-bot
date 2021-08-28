package org.example.kakaoworkbot;

import com.google.gson.*;
import org.example.kakaoworkbot.models.User;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Main {
    private static String appKey;
    private static User[] users;

    public static void main(String[] args) {
        appKey = System.getenv("APP_KEY");
        users = GetUsers();
        SendClosingPrices();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                users = GetUsers();
            }
        }, 0, 24 * 60 * 60 * 1000);

        GregorianCalendar usaStockMarketTodayClosingTime = new GregorianCalendar(TimeZone.getTimeZone("America/New_York"));
        usaStockMarketTodayClosingTime.set(usaStockMarketTodayClosingTime.get(Calendar.YEAR),
                usaStockMarketTodayClosingTime.get(Calendar.MONTH),
                usaStockMarketTodayClosingTime.get(Calendar.DAY_OF_MONTH),
                16,
                0,
                0);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SendClosingPrices();
            }
        }, usaStockMarketTodayClosingTime.getTime(), 24 * 60 * 60 * 1000);
    }

    private static void SendClosingPrices() {
        URI conversationsOpen;
        URI messagesSend;
        try {
            conversationsOpen = new URI("https://api.kakaowork.com/v1/conversations.open");
            messagesSend = new URI("https://api.kakaowork.com/v1/messages.send");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .build();
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        List<CompletableFuture<String>> responses = Arrays.stream(users).parallel()
                .map(user -> {
                    Map<Object, Object> data = new HashMap<>();
                    data.put("user_id", user.id);
                    HttpRequest httpRequest = HttpRequest.newBuilder(conversationsOpen)
                            .header("Authorization", "Bearer " + appKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(data)))
                            .build();
                    return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body)
                            .thenApply(JsonParser::parseString)
                            .thenApply(JsonElement::getAsJsonObject);
                })
                .map(CompletableFuture::join)
                .filter(jsonObject -> {
                    boolean success = jsonObject.get("success").getAsBoolean();
                    if (!success) {
                        System.out.println(gson.toJson(jsonObject.get("error")));
                    }
                    return success;
                }).map(jsonObject -> jsonObject.get("conversation").getAsJsonObject()
                        .get("id").getAsString())
                .map(conversationId -> {
                    Map<Object, Object> data = new HashMap<>();
                    data.put("conversation_id", conversationId);
                    data.put("text", "text");
                    HttpRequest httpRequest = HttpRequest.newBuilder(messagesSend)
                            .header("Authorization", "Bearer " + appKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(data)))
                            .build();
                    return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body);
                })
                .collect(Collectors.toList());
        for (CompletableFuture<String> response : responses) {
            System.out.println(gson.toJson(response.join()));
        }
    }

    private static User[] GetUsers() {
        URI uri;
        try {
            uri = new URI("https://api.kakaowork.com/v1/users.list");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return new User[0];
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .build();
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + appKey)
                .GET()
                .build();
        String response = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join();
        JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
        boolean success = jsonObject.get("success").getAsBoolean();
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        if (!success) {
            System.out.println(gson.toJson(jsonObject.get("error")));
            return new User[0];
        }
        return gson.fromJson(jsonObject.get("users"), User[].class);
    }
}
