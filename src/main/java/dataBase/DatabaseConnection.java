package dataBase;

import org.apache.log4j.Logger;
import org.telegram.telegrambots.meta.api.objects.Message;

import org.telegram.telegrambots.meta.api.objects.User;
import telegramBot.NullCheck;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;


public class DatabaseConnection {
    private static final String url = "jdbc:postgresql://10.8.0.1:5432/word_bot_db";
    private static final String username = "postgres";
    private static final String password = "56485648";
    private static Connection connection = null;
    private static final Logger logger = Logger.getLogger(DatabaseConnection.class);


    private static void connect() {
        try {
            connection = DriverManager.getConnection(url, username, password);
            System.out.println("Connection successful");
            logger.info("НОВОЕ подключение к БД выполнено успешно.");
        } catch (SQLException e) {
            logger.error("Ошибка при подлючении к БД" + e);
            e.printStackTrace();
        }
    }

    public static Connection getConnection() {
        if (connection == null) {
            connect();
        }
        return connection;
    }


}
