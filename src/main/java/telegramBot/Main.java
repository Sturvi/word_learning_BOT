package telegramBot;

import admin.Admin;
import admin.AdminsData;
import dataBase.DatabaseConnection;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import telegramBot.user.User;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static Map<Long, User> userMap = new HashMap<>();
    public static Admin admin = new Admin();

    public static void main(String[] args) {
        DatabaseConnection.connect();

        File backupDir = new File("backupDir");

        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        Backup backup = new Backup();
        backup.start();

        TelegramBotsApi telegramBotsApi = null;
        try {
            telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new TelegramApiConnect());
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public static void backupUserMapAndAdmin() {
        try (FileOutputStream fos = new FileOutputStream("backupDir/userMap.txt");
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(userMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void restoreUserMapAndAdmin() {
        try (FileInputStream fis = new FileInputStream("backupDir/userMap.txt");
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            userMap = (Map<Long, User>) ois.readObject();
            System.out.println("Map containing User objects read from userMap.txt: " + userMap);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

}