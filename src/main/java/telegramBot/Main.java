package telegramBot;

import Exceptions.WordTypeException;
import admin.Admin;

import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Random;


public class Main {

    public static Admin admin = new Admin();
    private static final Logger logger = Logger.getLogger(Main.class);
    private static Integer count = 1;


    synchronized public static Integer getCount() {
        return count;
    }

    synchronized public static void setCount() {
        Main.count++;
    }

    public static void main(String[] args) {
        logger.info("Запуск программы");

        for (int i = 0; i < 5; i++) {
            Runnable runnable = () -> {
                var englishWords = new ArrayList<String>();
                do {
                    Connection connection = DatabaseConnection.getConnection();
                    try (PreparedStatement preparedStatement = connection.prepareStatement(
                            "SELECT english_word FROM word_contexts WHERE word_contexts.usage_examples IS null " +
                                    "ORDER BY random() LIMIT 10;"
                    )) {
                        ResultSet resultSet = preparedStatement.executeQuery();

                        while (resultSet.next()) {
                            englishWords.add(resultSet.getString("english_word"));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }



                    for (String word : englishWords) {
                        try {
                            Thread.sleep(new Random().nextInt(1500));
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        try {
                            addContextToDataBase(word);
                            logger.info("Cлово " + word + " успешно добавлено " + getCount());
                            setCount();
                        } catch (Exception e) {
                            logger.info("Cлово " + word + " не добавлено");
                        }
                    }

                } while (!englishWords.isEmpty());
            };
            new Thread(runnable).start();
        }

        logger.info("КОНЕЦ");


/*        TelegramBotsApi telegramBotsApi;
        try {
            telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new TelegramApiConnect());
        } catch (TelegramApiException e) {
            logger.error("КОРОЧЕ ПИСЕЦ БОТУ! :-) " + e);
            throw new RuntimeException(e);
        }*/
    }

    private static void addContextToDataBase(String word) {
        logger.info("Старт метода Word.addContextToDataBase");
        Connection connection = DatabaseConnection.getConnection();
        String content;

        try {
            content = Api.getResponse(word, "usage_examples");
        } catch (IOException e) {
            logger.error("Ошибка получения контекста из API " + e);
            throw new RuntimeException(e);
        }

        String sql = "INSERT INTO word_contexts (english_word, usage_examples) VALUES (?, ?) " +
                "ON CONFLICT (english_word) DO UPDATE SET usage_examples = ?, context = word_contexts.context;";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, word);
            preparedStatement.setString(2, content);
            preparedStatement.setString(3, content);

            preparedStatement.execute();
            logger.info("Контекст успешно добавлен в Базу данных");
        } catch (SQLException e) {
            logger.error("Ошибка добавления контекста в Базу данных");
            throw new RuntimeException(e);
        }
    }
}