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

    private static final Logger logger = Logger.getLogger(WordsInDatabase.class);
    private static final NullCheck nullCheck = () -> logger;


    /*Метод получает список слов пользователя, указанного типа. Если тип "learning", список слов на изучении.
    Если тип "repetition", список слов на повторении (по уровням). Возвращает список в виде строки.*/
    public static @Nullable String getWordList(Long userId, String list_type) {
        nullCheck.checkForNull("getWordList ", userId, list_type);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWordList Connection ", connection);
        StringBuilder stringBuilder = new StringBuilder();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT w.english_word, w.russian_word, uwl.timer_value " +
                        "FROM words w " +
                        "JOIN user_word_lists uwl ON w.word_id = uwl.word_id " +
                        "WHERE uwl.user_id = ? AND uwl.list_type = ?"
        )) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, list_type);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (list_type.equalsIgnoreCase("learning")) {
                stringBuilder.append("Список слов на изучении:\n");
                while (resultSet.next()) {
                    String englishWord = resultSet.getString("english_word");
                    String russianWord = resultSet.getString("russian_word");
                    stringBuilder.append(englishWord).append("  -  ").append(russianWord).append("\n");
                }
            } else if (list_type.equalsIgnoreCase("repetition")) {
                Map<Integer, StringBuilder> repetitionWords = new HashMap<>();
                while (resultSet.next()) {
                    int timerValue = resultSet.getInt("timer_value");

                    String englishWord = resultSet.getString("english_word");
                    String russianWord = resultSet.getString("russian_word");

                    if (!repetitionWords.containsKey(timerValue)) repetitionWords.put(timerValue, new StringBuilder());
                    repetitionWords.get(timerValue).append(englishWord).append("  -  ").append(russianWord).append("\n");
                }

                for (int i = 1; i < 7; i++) {
                    if (repetitionWords.containsKey(i)) {
                        stringBuilder.append("Слова на повторении ").append(i).append(" уровня \n").append(repetitionWords.get(i)).append("\n");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Получение листа из БД" + e);
            throw new RuntimeException(e);
        }

        String result = stringBuilder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /*Метод обновляет прогресс изучения слова в зависимости от его нахождения в списке и количества повторений.
    Важно передать непустые значения userId и word.*/
    public static void updateWordProgress(Long userId, Word word) {
        nullCheck.checkForNull("changeWordListType ", userId, word);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("changeWordListType connection ", connection);

        String listType = getListTypeFromDB(userId, word);

        if (listType.equalsIgnoreCase("learning")) {
            updateWordProgressInDB("repetition", userId, word);
            logger.info("Слово переведено в словарь повторения");
        } else {
            Integer timerValue = getTimerValueFromDB(userId, word);
            if (timerValue < 7) {
                updateWordProgressInDB(listType, userId, word);
                logger.info("Слово обновлено в словаре повторения");
            } else {
                updateWordProgressInDB("learned", userId, word);
                logger.info("Слово переведено в словарь выученных");
            }
        }
    }

    /*Метод обновляет прогресс изучения слова в базе данных пользователя.
    В таблице "user_word_lists" обновляются поля "list_type", "timer_value" и "last_repetition_time".
    Входные параметры: тип списка, количество повторений, ID пользователя и объект слова.*/
    private static void updateWordProgressInDB(String listType, Long userId, Word word) {
        nullCheck.checkForNull("updateWordProgressInDB ", listType, userId, word);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("updateWordProgressInDB Connection ", connection);

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "UPDATE user_word_lists " +
                        "SET list_type = ?, timer_value = timer_value + ?, last_repetition_time = now() " +
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
    private static Integer getTimerValueFromDB(Long userId, Word word) {
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
