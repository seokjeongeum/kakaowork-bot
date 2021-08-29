package org.example.kakaoworkbot;

import com.google.gson.*;
import org.example.kakaoworkbot.models.*;
import org.example.kakaoworkbot.models.conversations.Conversation;
import org.example.kakaoworkbot.models.conversations.ConversationsOpenRequest;
import org.example.kakaoworkbot.models.conversations.ConversationsOpenResponse;
import org.example.kakaoworkbot.models.messages.*;
import org.example.kakaoworkbot.models.users.User;
import org.example.kakaoworkbot.models.users.UsersListResponse;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static String appKey;
    private static List<User> users;
    private static String fmpApiKey;
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .build();
    private static List<Conversation> conversations;

    public static void main(String[] args) {
        appKey = System.getenv("APP_KEY");
        users = GetUsers();
        fmpApiKey = System.getenv("FMP_API_KEY");
        conversations = OpenConversations();
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
                conversations = OpenConversations();
                SendClosingPrices();
            }
        }, usaStockMarketTodayClosingTime.getTime(), 24 * 60 * 60 * 1000);
    }

    private static List<Conversation> OpenConversations() {
        URI conversationsOpen;
        try {
            conversationsOpen = new URI("https://api.kakaowork.com/v1/conversations.open");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        return users.parallelStream()
                .map(user -> {
                    ConversationsOpenRequest conversationsOpenRequest = new ConversationsOpenRequest();
                    conversationsOpenRequest.user_id = user.id;
                    HttpRequest httpRequest = HttpRequest.newBuilder(conversationsOpen)
                            .header("Authorization", "Bearer " + appKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(conversationsOpenRequest)))
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
                }).map(conversationsOpenResponse -> conversationsOpenResponse.conversation)
                .collect(Collectors.toList());
    }

    private static void SendClosingPrices() {
        URI messagesSend;
        URI nasdaq100Api;
        URI snp500Api;
        try {
            messagesSend = new URI("https://api.kakaowork.com/v1/messages.send");
            nasdaq100Api = new URI("https://financialmodelingprep.com/api/v3/quote/%5ENDX?apikey=" + fmpApiKey);
            snp500Api = new URI("https://financialmodelingprep.com/api/v3/quote/%5EGSPC?apikey=" + fmpApiKey);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
        List<TextBlock> textBlocks = Stream.of(nasdaq100Api, snp500Api).parallel()
                .map(uri -> httpClient.sendAsync(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()))
                .map(CompletableFuture::join)
                .map(HttpResponse::body)
                .map(body -> gson.fromJson(body, Index[].class)[0])
                .map(index -> String.format("%s: %.2f (%+.2f, %+.2f%%)", index.name, index.price, index.change, index.changesPercentage))
                .map(text -> new TextBlock(text, false))
                .collect(Collectors.toList());

        List<Block> blocks = new ArrayList<>();
        blocks.add(new HeaderBlock("Index end prices", "yellow"));
        for (TextBlock textBlock : textBlocks) {
            blocks.add(textBlock);
            blocks.add(new DividerBlock());
        }
        blocks.remove(blocks.size() - 1);

        List<CompletableFuture<Response>> responses = conversations.parallelStream()
                .map(conversation -> conversation.id)
                .map(conversationId -> {
                    MessagesSendRequest messagesSendRequest = new MessagesSendRequest(conversationId, "Index end prices");
                    messagesSendRequest.blocks = blocks;
                    HttpRequest httpRequest = HttpRequest.newBuilder(messagesSend)
                            .header("Authorization", "Bearer " + appKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(messagesSendRequest)))
                            .build();
                    return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body)
                            .thenApply(JsonParser::parseString)
                            .thenApply(jsonElement -> gson.fromJson(jsonElement, Response.class));
                })
                .collect(Collectors.toList());
        for (CompletableFuture<Response> responseCompletableFuture : responses) {
            Response response = responseCompletableFuture.join();
            if (!response.success) {
                System.out.println(gson.toJson(response.error));
            }
        }
    }

    private static List<User> GetUsers() {
        URI uri;
        try {
            uri = new URI("https://api.kakaowork.com/v1/users.list");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return Collections.emptyList();
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
            return Collections.emptyList();
        }
        return usersListResponse.users;
    }
}
