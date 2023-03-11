package dataBase;

import org.telegram.telegrambots.meta.api.objects.Message;

import java.sql.*;

public class DatabaseConnection {
    private static final String url = "jdbc:postgresql://localhost:5432/word_db";
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

        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE user_id = ?");
            ps.setLong(1, user_id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                PreparedStatement ps2 = connection.prepareStatement("INSERT INTO users (user_id, first_name, last_name, username) VALUES (?, ?, ?, ?)");
                ps2.setLong(1, user_id);
                ps2.setString(2, first_name);
                ps2.setString(3, last_name);
                ps2.setString(4, username);
                ps2.executeUpdate();
                System.out.println("User " + username + " added to database");
            } else {
                System.out.println("User " + username + "  already exists in database");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public static Connection getConnection() {
        return connection;
    }
}
