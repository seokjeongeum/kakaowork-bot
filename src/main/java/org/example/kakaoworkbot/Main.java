package org.example.kakaoworkbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.example.kakaoworkbot.models.financialmodelingprep.Index;
import org.example.kakaoworkbot.models.financialmodelingprep.IsTheMarketOpen;
import org.example.kakaoworkbot.models.kakaowork.Response;
import org.example.kakaoworkbot.models.kakaowork.conversations.Conversation;
import org.example.kakaoworkbot.models.kakaowork.conversations.ConversationsOpenRequest;
import org.example.kakaoworkbot.models.kakaowork.conversations.ConversationsOpenResponse;
import org.example.kakaoworkbot.models.kakaowork.messages.MessagesSendRequest;
import org.example.kakaoworkbot.models.kakaowork.messages.blocks.Block;
import org.example.kakaoworkbot.models.kakaowork.messages.blocks.ButtonBlock;
import org.example.kakaoworkbot.models.kakaowork.messages.blocks.HeaderBlock;
import org.example.kakaoworkbot.models.kakaowork.messages.blocks.TextBlock;
import org.example.kakaoworkbot.models.kakaowork.users.User;
import org.example.kakaoworkbot.models.kakaowork.users.UsersListResponse;

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
    private static final int DAY = 24 * 60 * 60 * 1000;
    private static List<User> users;

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
                            .header("Authorization", "Bearer " + APP_KEY)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(conversationsOpenRequest)))
                            .build();
                    return HTTP_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body)
                            .thenApply(JsonParser::parseString)
                            .thenApply(jsonElement -> GSON.fromJson(jsonElement, ConversationsOpenResponse.class));
                })
                .map(CompletableFuture::join)
                .filter(conversationsOpenResponse -> {
                    if (!conversationsOpenResponse.success) {
                        System.out.println(GSON.toJson(conversationsOpenResponse.error));
                    }
                    return conversationsOpenResponse.success;
                }).map(conversationsOpenResponse -> conversationsOpenResponse.conversation)
                .collect(Collectors.toList());
    }

    private static List<Conversation> conversations;
    private static List<List<Block>> blocks;

    private static void SendClosingPrices() {
        URI messagesSend;
        try {
            messagesSend = new URI("https://api.kakaowork.com/v1/messages.send");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        List<CompletableFuture<Response>> responses = conversations.parallelStream()
                .map(conversation -> conversation.id)
                .flatMap(conversationId -> blocks.parallelStream()
                        .map(indexBlocks -> {
                            MessagesSendRequest messagesSendRequest = new MessagesSendRequest(conversationId, "Index end prices");
                            messagesSendRequest.blocks = indexBlocks;
                            HttpRequest httpRequest = HttpRequest.newBuilder(messagesSend)
                                    .header("Authorization", "Bearer " + APP_KEY)
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(messagesSendRequest)))
                                    .build();
                            return HTTP_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                                    .thenApply(HttpResponse::body)
                                    .thenApply(JsonParser::parseString)
                                    .thenApply(jsonElement -> GSON.fromJson(jsonElement, Response.class));
                        }))
                .collect(Collectors.toList());
        for (CompletableFuture<Response> responseCompletableFuture : responses) {
            Response response = responseCompletableFuture.join();
            if (!response.success) {
                System.out.println(GSON.toJson(response.error));
            }
        }
    }

    private static boolean isTheStockMarketOpen;

    public static void main(String[] args) {
        Timer timer = new Timer();

        GregorianCalendar localMidnight = new GregorianCalendar();
        localMidnight.set(localMidnight.get(Calendar.YEAR),
                localMidnight.get(Calendar.MONTH),
                localMidnight.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                users = GetUsers();
            }
        }, localMidnight.getTime(), DAY);

        GregorianCalendar usaToday = new GregorianCalendar(TimeZone.getTimeZone("America/New_York"));
        GregorianCalendar usaStockMarketTodayClosingTime = new GregorianCalendar(TimeZone.getTimeZone("America/New_York"));
        if (localMidnight.get(Calendar.DAY_OF_YEAR) == usaStockMarketTodayClosingTime.get(Calendar.DAY_OF_YEAR)) {
            usaToday.add(Calendar.DAY_OF_YEAR, -1);
            usaStockMarketTodayClosingTime.add(Calendar.DAY_OF_YEAR, -1);
        }

        usaToday.set(usaToday.get(Calendar.YEAR),
                usaToday.get(Calendar.MONTH),
                usaToday.get(Calendar.DAY_OF_MONTH),
                15,
                55,
                0);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                isTheStockMarketOpen = GetIsTheStockMarketOpen();
            }
        }, usaToday.getTime(), DAY);

        usaStockMarketTodayClosingTime.set(usaStockMarketTodayClosingTime.get(Calendar.YEAR),
                usaStockMarketTodayClosingTime.get(Calendar.MONTH),
                usaStockMarketTodayClosingTime.get(Calendar.DAY_OF_MONTH),
                16,
                0,
                0);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isTheStockMarketOpen || USER_ID != null) {
                    conversations = OpenConversations();
                    blocks = CreateBlocks();
                    SendClosingPrices();
                }
            }
        }, usaStockMarketTodayClosingTime.getTime(), DAY);
    }

    private static boolean GetIsTheStockMarketOpen() {
        URI isTheMarketOpen;
        try {
            isTheMarketOpen = new URI("https://financialmodelingprep.com/api/v3/is-the-market-open?apikey=" + FMP_API_KEY);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return false;
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(isTheMarketOpen)
                .header("Authorization", "Bearer " + APP_KEY)
                .build();
        return HTTP_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JsonParser::parseString)
                .thenApply(jsonElement -> GSON.fromJson(jsonElement, IsTheMarketOpen.class))
                .join()
                .isTheStockMarketOpen;
    }

    private static List<List<Block>> CreateBlocks() {
        URI nasdaq100Api;
        URI snp500Api;
        try {
            nasdaq100Api = new URI("https://financialmodelingprep.com/api/v3/quote/%5ENDX?apikey=" + FMP_API_KEY);
            snp500Api = new URI("https://financialmodelingprep.com/api/v3/quote/%5EGSPC?apikey=" + FMP_API_KEY);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        return Stream.of(nasdaq100Api, snp500Api).parallel()
                .map(uri -> HTTP_CLIENT.sendAsync(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()))
                .map(CompletableFuture::join)
                .map(HttpResponse::body)
                .map(JsonParser::parseString)
                .map(jsonElement -> GSON.fromJson(jsonElement, Index[].class)[0])
                .map(index -> {
                    List<Block> indexBlocks = new ArrayList<>();
                    HeaderBlock.Style style;
                    if (index.change > 0) {
                        style = HeaderBlock.Style.red;
                    } else if (index.change < 0) {
                        style = HeaderBlock.Style.blue;
                    } else {
                        style = HeaderBlock.Style.yellow;
                    }
                    indexBlocks.add(new HeaderBlock(index.name, style));
                    indexBlocks.add(new TextBlock(String.format("%.2f (%+.2f, %+.2f%%)", index.price, index.change, index.changesPercentage), false));
                    ButtonBlock buttonBlock = new ButtonBlock("Open in FMP", "default");
                    buttonBlock.action_type = ButtonBlock.ActionType.open_system_browser;
                    buttonBlock.value = "https://financialmodelingprep.com/index-summary/" + index.symbol;
                    indexBlocks.add(buttonBlock);
                    return indexBlocks;
                })
                .collect(Collectors.toList());
    }

    private static final String APP_KEY = System.getenv("APP_KEY");
    private static final String FMP_API_KEY = System.getenv("FMP_API_KEY");
    private static final String USER_ID = System.getenv("USER_ID");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .build();

    private static List<User> GetUsers() {
        if (USER_ID != null) {
            User user = new User();
            user.id = Integer.parseInt(USER_ID);
            return List.of(user);
        }
        URI usersList;
        try {
            usersList = new URI("https://api.kakaowork.com/v1/users.list");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(usersList)
                .header("Authorization", "Bearer " + APP_KEY)
                .build();
        UsersListResponse usersListResponse = HTTP_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(JsonParser::parseString)
                .thenApply(jsonElement -> GSON.fromJson(jsonElement, UsersListResponse.class))
                .join();
        if (!usersListResponse.success) {
            System.out.println(GSON.toJson(usersListResponse.error));
            return Collections.emptyList();
        }
        return usersListResponse.users;
    }
}
