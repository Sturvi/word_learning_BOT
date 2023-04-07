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

    public static void checkUser(User user) {
        NullCheck nullChecker = () -> logger;
        nullChecker.checkForNull("checkUser", user);

        Connection connection = getConnection();


        if (connection == null) {
            logger.error("checkUser Ошибка подключения к БД. connection вернулся null");
            return;
        }

        long user_id = user.getId();
        String first_name = user.getFirstName();
        String last_name = user.getLastName();
        String username = user.getUserName();
        LocalDateTime localDateTime = LocalDateTime.now(ZoneId.of("UTC"));

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (user_id, first_name, last_name, username, last_contact_time) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT (user_id) " +
                        "DO UPDATE SET first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, " +
                        "username = EXCLUDED.username, last_contact_time = EXCLUDED.last_contact_time")) {
            ps.setLong(1, user_id);
            ps.setString(2, first_name);
            ps.setString(3, last_name);
            ps.setString(4, username);
            ps.setTimestamp(5, Timestamp.valueOf(localDateTime));
            ps.executeUpdate();
            logger.info("Пользователь " + username + " добавлен/обновлен в БД");
        } catch (SQLException e) {
            logger.error("Ошибка добавления/обновления пользователя в БД" + e);
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_menu (user_id, menu_name) " +
                        "VALUES (?, ?) " +
                        "ON CONFLICT DO NOTHING")) {
            ps.setLong(1, user_id);
            ps.setString(2, "NULL");
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Ошибка в добавлении меню по умолчанию в БД " + e);
        }
    }

    public static Connection getConnection() {
        if (connection == null) {
            connect();
        }
        return connection;
    }


}
