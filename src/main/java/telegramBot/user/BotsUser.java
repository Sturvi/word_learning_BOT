package telegramBot.user;

import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import telegramBot.NullCheck;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BotsUser implements Serializable {

    private static final Logger logger = Logger.getLogger(BotsUser.class);

    private BotsUser() {

    }

    public static @Nullable String getUserMenu(Long userId) {
        Connection connection = DatabaseConnection.getConnection();

        if (connection == null) logger.error("getUserMenu Ошибка подключения к БД. connection вернулся null");

        try {
            assert connection != null;
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT menu_name FROM user_menu WHERE user_id = ?")) {
                preparedStatement.setLong(1, userId);
                ResultSet resultSet = preparedStatement.executeQuery();

                if (resultSet.next())
                    return resultSet.getString("menu_name");
            }
        } catch (SQLException e) {
            logger.error("Не удалось получить меню из БД" + e);
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void setMenu(Long userId, String menuName) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("setMenu", userId, menuName);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("setMenu connection ", connection);

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE  user_menu SET menu_name = ? WHERE user_id = ?")) {
            ps.setString(1, menuName);
            ps.setLong(2, userId);
            ps.executeUpdate();
            logger.info("Меню для пользователя изменено");
        } catch (SQLException e) {
            logger.error("setMenu ОШИБКА смены меню в БД " + e);
        }
    }

    public static @NotNull String getStatistic(Long userId) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getStatistic", userId);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getStatistic Connection", connection);
        Integer learningCount = null;
        Integer repetitionCount = null;
        Integer learnedCount = null;

        try (PreparedStatement preparedStatement = connection.prepareStatement("" +
                "SELECT " +
                "(SELECT COUNT(word_id) FROM user_word_lists WHERE user_id = ? AND list_type = 'learning') AS learning_count," +
                "(SELECT COUNT(word_id) FROM user_word_lists WHERE user_id = ? AND list_type = 'repetition') AS repetition_count," +
                "(SELECT COUNT(word_id) FROM user_word_lists WHERE user_id = ? AND list_type = 'learned') AS learned_count;")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setLong(2, userId);
            preparedStatement.setLong(3, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                learningCount = resultSet.getInt("learning_count");
                repetitionCount = resultSet.getInt("repetition_count");
                learnedCount = resultSet.getInt("learned_count");
            }
            logger.info("Данные статистики из БД получены");

        } catch (SQLException e) {
            logger. error("ОШИБКА получении данных статистики из БД " + e);
            throw new RuntimeException(e);
        }

        return "Изученные слова: " + learnedCount + "\n" +
                "Слова на повторении: " + repetitionCount + "\n" +
                "Слова на изучении: " + learningCount;
    }
}
