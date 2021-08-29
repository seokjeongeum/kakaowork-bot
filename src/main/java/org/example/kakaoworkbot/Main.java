package org.example.kakaoworkbot;

import com.google.gson.*;
import org.example.kakaoworkbot.models.*;

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
    private static String fmpApiKey;
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .build();

    public static void main(String[] args) {
        appKey = System.getenv("APP_KEY");
        users = GetUsers();
        fmpApiKey = System.getenv("FMP_API_KEY");
        SendClosingPrices();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                users = GetUsers();
            }
        }, 0, 24 * 60 * 60 * 1000);

        //TODO: Don't send messages on weekends and holidays
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

    //TODO: Separate getting conversation ids
    private static void SendClosingPrices() {
        URI conversationsOpen;
        URI messagesSend;
        URI nasdaq100Api;
        URI snp500Api;
        try {
            conversationsOpen = new URI("https://api.kakaowork.com/v1/conversations.open");
            messagesSend = new URI("https://api.kakaowork.com/v1/messages.send");
            nasdaq100Api = new URI("https://financialmodelingprep.com/api/v3/quote/%5ENDX?apikey=" + fmpApiKey);
            snp500Api = new URI("https://financialmodelingprep.com/api/v3/quote/%5EGSPC?apikey=" + fmpApiKey);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
        var nasdaq100Body = httpClient.sendAsync(HttpRequest.newBuilder(nasdaq100Api).build(), HttpResponse.BodyHandlers.ofString())
                .join()
                .body();
        var snp500Body = httpClient.sendAsync(HttpRequest.newBuilder(snp500Api).build(), HttpResponse.BodyHandlers.ofString())
                .join()
                .body();
        var indexes = new Index[]{gson.fromJson(nasdaq100Body, Index[].class)[0], gson.fromJson(snp500Body, Index[].class)[0]};
        List<CompletableFuture<Response>> responses = Arrays.stream(users).parallel()
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
                            .thenApply(jsonElement -> gson.fromJson(jsonElement, ConversationsOpenResponse.class));
                })
                .map(CompletableFuture::join)
                .filter(conversationsOpenResponse -> {
                    if (!conversationsOpenResponse.success) {
                        System.out.println(gson.toJson(conversationsOpenResponse.error));
                    }
                    return conversationsOpenResponse.success;
                }).map(conversationsOpenResponse -> conversationsOpenResponse.conversation.id)
                .map(conversationId -> {
                    ArrayList<CompletableFuture<Response>> futures = new ArrayList<>();
                    for (Index index : indexes) {
                        Map<Object, Object> data = new HashMap<>();
                        data.put("conversation_id", conversationId);
                        //TODO: Change the text color subject to the price change
                        data.put("text", String.format("%s: %.3f (%.3f, %.2f%%)", index.name, index.price, Math.abs(index.change), Math.abs(index.changesPercentage)));
                        HttpRequest httpRequest = HttpRequest.newBuilder(messagesSend)
                                .header("Authorization", "Bearer " + appKey)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(data)))
                                .build();
                        futures.add(httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                                .thenApply(HttpResponse::body)
                                .thenApply(JsonParser::parseString)
                                .thenApply(jsonElement -> gson.fromJson(jsonElement, Response.class)));
                    }
                    return futures;
                })
                .flatMap(Collection::parallelStream)
                .collect(Collectors.toList());
        for (CompletableFuture<Response> responseCompletableFuture : responses) {
            Response response = responseCompletableFuture.join();
            if (!response.success) {
                System.out.println(gson.toJson(response.error));
            }
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
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + appKey)
                .GET()
                .build();
        UsersListResponse usersListResponse = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JsonParser::parseString)
                .thenApply(jsonElement -> gson.fromJson(jsonElement, UsersListResponse.class))
                .join();
        if (!usersListResponse.success) {
            System.out.println(gson.toJson(usersListResponse.error));
            return new User[0];
        }
        return usersListResponse.users;
    }
}
