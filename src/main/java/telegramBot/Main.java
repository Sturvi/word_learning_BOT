package telegramBot;

import admin.Admin;

import dataBase.DatabaseConnection;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


public class Main {

    public static Admin admin = new Admin();

    public static void main(String[] args) {
        DatabaseConnection.connect();

        TelegramBotsApi telegramBotsApi = null;
        try {
            telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new TelegramApiConnect());
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}