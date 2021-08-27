package org.example.kakaoworkbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.example.kakaoworkbot.models.User;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
                0);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SendClosingPrices();
            }
        }, usaStockMarketTodayClosingTime.getTime(), 24 * 60 * 60 * 1000);
    }

    private static void SendClosingPrices() {
        //Open conversations
        URI uri;
        try {
            uri = new URI("https://api.kakaowork.com/v1/conversations.open");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .build();
        List<CompletableFuture<String>> responses = Arrays.stream(users)
                .parallel()
                .map(user -> {
                    Map<Object, Object> data = new HashMap<>();
                    data.put("user_id", user.id);
                    HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                            .header("Authorization", "Bearer " + appKey)
                            .POST(ofFormData(data))
                            .build();
                    return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body);
                }).collect(Collectors.toList());
        for (CompletableFuture<String> response : responses) {
            JsonElement jsonElement;
            try {
                jsonElement = JsonParser.parseString(response.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                continue;
            }
            boolean success = jsonElement.getAsJsonObject()
                    .get("success")
                    .getAsBoolean();
            if (!success) {
                Gson gson = new GsonBuilder()
                        .setPrettyPrinting()
                        .create();
                System.out.println(gson.toJson(jsonElement.getAsJsonObject()
                        .get("error")));
                /*
2021-08-27T13:25:16.222692000Z {
2021-08-27T13:25:16.222741000Z   "code": "missing_parameter",
2021-08-27T13:25:16.222747500Z   "message": "user_id or user_ids must needed"
2021-08-27T13:25:16.222751600Z }
*/
            }
        }
        //TODO: Send closing prices
    }

    //https://mkyong.com/java/java-11-httpclient-examples/
    public static HttpRequest.BodyPublisher ofFormData(Map<Object, Object> data) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(builder.toString());
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
        String response;
        try {
            response = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new User[0];
        }
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
            return new User[0];
        }
        return gson.fromJson(jsonElement.getAsJsonObject()
                .get("users"), User[].class);
    }
}
