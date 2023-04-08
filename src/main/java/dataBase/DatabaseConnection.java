package dataBase;

import org.apache.log4j.Logger;

import java.sql.*;

public class DatabaseConnection {
    private static final String url = "jdbc:postgresql://10.8.0.1:5432/word_bot_db";
    private static final String username = "postgres";
    private static final String password = "56485648";
    private static Connection connection = null;
    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class);


    /**
     * Устанавливает соединение с базой данных.
     */
    private static void connect() {
        try {
            connection = DriverManager.getConnection(url, username, password);
            System.out.println("Connection successful");
            LOGGER.info("НОВОЕ подключение к БД выполнено успешно.");
        } catch (SQLException e) {
            LOGGER.error("Ошибка при подключении к БД" + e);
            e.printStackTrace();
        }
    }

    /**
     * Возвращает соединение с базой данных.
     * Если соединение еще не установлено, устанавливает его.
     * @return Соединение с базой данных.
     */
    public static Connection getConnection() {
        if (connection == null) {
            connect();
        }
        return connection;
    }
}
