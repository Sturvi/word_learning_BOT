package telegramBot.user;

import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import telegramBot.NullCheck;
import telegramBot.Word;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class WordsInDatabase {

    private static final String LEARNING_LIST_TYPE = "learning";
    private static final String REPETITION_LIST_TYPE = "repetition";
    private static final String LEARNED_LIST_TYPE = "learned";
    private static final String LIST_TYPE_FIELD = "list_type";
    private static final Logger logger = Logger.getLogger(WordsInDatabase.class);
    private static final NullCheck nullCheck = () -> logger;


    /**
     * Обновляет прогресс изучения слова для указанного пользователя, перемещая слово между списками (изучаемые, повторение, выученные)
     * в зависимости от текущего списка и количества повторений.
     *
     * @param userId идентификатор пользователя
     * @param word   объект слова
     */
    public static void updateUserWordProgress(Long userId, Word word) {
        // Проверка на null значений
        nullCheck.checkForNull("changeWordListType ", userId, word);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("changeWordListType connection ", connection);

        String listType = getListTypeFromDB(userId, word);

        // Переводим слово из списка "изучаемые" в список "повторение"
        if (listType.equalsIgnoreCase(LEARNING_LIST_TYPE)) {
            updateUserWordProgressInDB(REPETITION_LIST_TYPE, userId, word);
            logger.info("Слово переведено в словарь повторения");
        } else {
            // Обновляем прогресс слова в списке "повторение"
            Integer repetitions = getRepetitionsFromDB(userId, word);
            if (repetitions < 7) {
                updateUserWordProgressInDB(listType, userId, word);
                logger.info("Слово обновлено в словаре повторения");
            } else {
                // Переводим слово в список "выученные"
                updateUserWordProgressInDB(LEARNED_LIST_TYPE, userId, word);
                logger.info("Слово переведено в словарь выученных");
            }
        }
    }

    /**
     * Обновляет прогресс изучения слова в базе данных пользователя.
     * В таблице "user_word_lists" обновляются поля "list_type", "timer_value" и "last_repetition_time".
     *
     * @param listType тип списка (изучаемые, повторение, выученные)
     * @param userId   идентификатор пользователя
     * @param word     объект слова
     */
    private static void updateUserWordProgressInDB(String listType, Long userId, Word word) {
        // Проверка на null значений
        nullCheck.checkForNull("updateUserWordProgressInDB ", listType, userId, word);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("updateUserWordProgressInDB Connection ", connection);

        // Обновление прогресса слова в базе данных
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "UPDATE user_word_lists " +
                        "SET " + LIST_TYPE_FIELD + " = ?, timer_value = timer_value + ?, last_repetition_time = now() " +
                        "WHERE user_id = ? AND word_id = ?")) {
            preparedStatement.setString(1, listType);
            preparedStatement.setInt(2, 1);
            preparedStatement.setLong(3, userId);
            preparedStatement.setInt(4, word.getWordId());
            preparedStatement.execute();
            logger.info("Слово успешно переведено в другой словарь пользователя");
        } catch (SQLException e) {
            logger.error("Ошибка смены словаря в БД для пользователя" + e);
            throw new RuntimeException(e);
        }
    }

    /*Метод получает тип списка слов из базы данных по идентификатору пользователя и объекту слова.*/
    private static String getListTypeFromDB(Long userId, Word word) {
        Connection connection = DatabaseConnection.getConnection();
        String list_type = null;

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT list_type FROM user_word_lists WHERE user_id = ? AND word_id = ?")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setInt(2, word.getWordId());
            ResultSet resultSet = preparedStatement.executeQuery();
            nullCheck.checkForNull("editKeyboardAfterLeanedOrForgot resultSet ", resultSet);

            while (resultSet.next()) {
                list_type = resultSet.getString("list_type");
                logger.info("list_type Получен из БД");
            }
        } catch (SQLException e) {
            logger.error("editKeyboardAfterLeanedOrForgot Ошибка получения list_type из БД");
            throw new RuntimeException(e);
        }

        return list_type;
    }

    /*Метод получает из БД значение таймера для определенного пользователя и слова.
    В случае ошибки выбрасывает исключение.*/
    private static Integer getRepetitionsFromDB(Long userId, Word word) {
        nullCheck.checkForNull("getTimesValue ", userId, word);

        Connection connection = DatabaseConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT timer_value FROM user_word_lists WHERE user_id = ? AND word_id = ?")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setInt(2, word.getWordId());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                logger.info("Timer Value успешно получен");
                return resultSet.getInt("timer_value");
            } else
                throw new SQLException();
        } catch (SQLException e) {
            logger.error("ОШИБКА получения timer_value из БД");
            throw new RuntimeException(e);
        }
    }
}
