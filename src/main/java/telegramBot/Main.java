package telegramBot;

import admin.Admin;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static Map<Long, User> userMap = new HashMap<>();
    public static Admin admin = new Admin();

    public static void main(String[] args) {

        Admin admin = new Admin();
        TelegramBotsApi telegramBotsApi = null;
        try {
            telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new TelegramApiConnect());
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}