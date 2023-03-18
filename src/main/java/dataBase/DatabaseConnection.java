package dataBase;

import org.telegram.telegrambots.meta.api.objects.Message;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

public class DatabaseConnection {
    private static final String url = "jdbc:postgresql://185.125.200.162:5432/word_bot_db";
    private static final String username = "postgres";
    private static final String password = "56485648";
    private static Connection connection;

    public static void connect() {
        connection = null;
        try {
            connection = DriverManager.getConnection(url, username, password);
            System.out.println("Connection successful");
        } catch (SQLException e) {
            System.out.println("Connection failed");
            e.printStackTrace();
        }
    }

    public static void checkUser(Message message) {
        Connection connection = getConnection();

        if (connection == null) {
            System.out.println("Ошибка подключения к Базе Данных");
            return;
        }

        long user_id = message.getFrom().getId();
        String first_name = message.getFrom().getFirstName();
        String last_name = message.getFrom().getLastName();
        String username = message.getFrom().getUserName();
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
            System.out.println("User " + username + " has been added or updated in the database");
        } catch (SQLException e) {
            System.err.println("Error inserting/updating user: " + e.getMessage());
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_menu (user_id, menu_name) " +
                        "VALUES (?, ?) " +
                        "ON CONFLICT DO NOTHING")) {
            ps.setLong(1, user_id);
            ps.setString(2, "NULL");
            ps.executeUpdate();
            System.out.println("User " + username + " menu selected");
        } catch (SQLException e) {
            System.err.println("Error inserting user menu: " + e.getMessage());
        }
    }


    public static Connection getConnection() {
        return connection;
    }
}
