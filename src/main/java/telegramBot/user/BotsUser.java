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

    /*Метод getStatistic получает статистику из базы данных о словах, изучаемых пользователем.
    Использует NullCheck для проверки на null и PreparedStatement для выполнения запроса к БД.
    Результаты запроса форматируются в строку и возвращаются.*/
    public static @NotNull String getStatistic(Long userId) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getStatistic", userId);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getStatistic Connection", connection);

        StringBuilder stringBuilder = new StringBuilder();

        try (PreparedStatement preparedStatement = connection.prepareStatement("" +
                "SELECT " +
                "COUNT(CASE WHEN list_type = 'learning' THEN word_id END) AS learning_count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 1 THEN word_id END) AS repetition_1count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 2 THEN word_id END) AS repetition_2count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 3 THEN word_id END) AS repetition_3count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 4 THEN word_id END) AS repetition_4count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 5 THEN word_id END) AS repetition_5count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 6 THEN word_id END) AS repetition_6count, " +
                "COUNT(CASE WHEN list_type = 'learned' THEN word_id END) AS learned_count " +
                "FROM user_word_lists WHERE user_id = ?")){
            preparedStatement.setLong(1, userId);

            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                stringBuilder.append("Слова на изучении: ").append(resultSet.getInt("learning_count")).append("\n");
                if (resultSet.getInt("repetition_1count") != 0){
                    stringBuilder.append("Слова на повторении 1 уровня: ").append(resultSet.getInt("repetition_1count")).append("\n");
                }
                if (resultSet.getInt("repetition_2count") != 0){
                    stringBuilder.append("Слова на повторении 2 уровня: ").append(resultSet.getInt("repetition_2count")).append("\n");
                }
                if (resultSet.getInt("repetition_3count") != 0){
                    stringBuilder.append("Слова на повторении 3 уровня️: ").append(resultSet.getInt("repetition_3count")).append("\n");
                }
                if (resultSet.getInt("repetition_4count") != 0){
                    stringBuilder.append("Слова на повторении 4 уровня️: ").append(resultSet.getInt("repetition_4count")).append("\n");
                }
                if (resultSet.getInt("repetition_5count") != 0){
                    stringBuilder.append("Слова на повторении 5 уровня️: ").append(resultSet.getInt("repetition_5count")).append("\n");
                }
                if (resultSet.getInt("repetition_6count") != 0){
                    stringBuilder.append("Слова на повторении 6 уровня️: ").append(resultSet.getInt("repetition_6count")).append("\n");
                }
                stringBuilder.append("Изученные слова: ").append(resultSet.getInt("learned_count")).append("\n");
            }
            logger.info("Данные статистики из БД получены");

        } catch (SQLException e) {
            logger. error("ОШИБКА получении данных статистики из БД " + e);
            throw new RuntimeException(e);
        }

        return stringBuilder.toString();
    }
}
