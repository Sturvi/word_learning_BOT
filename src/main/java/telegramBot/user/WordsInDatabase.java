package telegramBot.user;

import Exceptions.TranslationException;
import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
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




    public static @Nullable String getWordList(Long userId, String list_type) {
        nullCheck.checkForNull("getWordList ", userId, list_type);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWordList Connection ", connection);
        StringBuilder stringBuilder = new StringBuilder();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT russian_word, english_word FROM words WHERE word_id IN (" +
                        "SELECT word_id FROM user_word_lists WHERE user_id = ? AND list_type = ?)"
        )) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, list_type);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String englishWord = resultSet.getString("english_word");
                String russianWord = resultSet.getString("russian_word");
                stringBuilder.append(englishWord).append("  -  ").append(russianWord).append("\n");
            }
        } catch (SQLException e) {
            logger.error("Получение листа из БД" + e);
            throw new RuntimeException(e);
        }

        String result = stringBuilder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    public static void removeWord(Long userId, @NotNull String text) {
        nullCheck.checkForNull("removeWord", userId, text);

        String[] word = text.split(" - ");
        nullCheck.checkForNull("removeWord word ", word[0], word[1]);
        word[0] = word[0].trim();
        word[1] = word[1].trim();

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("removeWord Connection", connection);
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "DELETE FROM user_word_lists where user_id = ? AND word_id = (" +
                        "SELECT word_id FROM words WHERE" +
                        "(russian_word = ? AND english_word = ?) OR " +
                        "(english_word = ? AND russian_word = ?))")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, word[0]);
            preparedStatement.setString(3, word[1]);
            preparedStatement.setString(4, word[0]);
            preparedStatement.setString(5, word[1]);
            preparedStatement.execute();
            logger.info("Слово успешно удалено из словаря");
        } catch (SQLException e) {
            logger.error("Не удалось удалить слово из словаря" + e);
            throw new RuntimeException(e);
        }
    }

    public static void updateWordProgress(Long userId, Word word) {
        nullCheck.checkForNull("changeWordListType ", userId, word);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("changeWordListType connection ", connection);

        String listType = getListTypeFromDB(userId, word);

        if (listType.equalsIgnoreCase("learning")) {
            updateWordProgressInDB("repetition", 1, userId, word);
            logger.info("Слово переведено в словарь повторения");
        } else {
            Integer timerValue = getTimerValueFromDB(userId, word);
            if (timerValue < 7) {
                updateWordProgressInDB(listType, 1, userId, word);
                logger.info("Слово обновлено в словаре повторения");
            } else {
                updateWordProgressInDB("learned", 1, userId, word);
                logger.info("Слово переведено в словарь выученных");
            }
        }
    }

    private static void updateWordProgressInDB(String listType, Integer timesValue, Long userId, Word word) {
        nullCheck.checkForNull("updateWordProgressInDB ", listType, timesValue, userId, word);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("updateWordProgressInDB Connection ", connection);

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "UPDATE user_word_lists " +
                        "SET list_type = ?, timer_value = timer_value + ?, last_repetition_time = now() " +
                        "WHERE user_id = ? AND word_id = ?")) {
            preparedStatement.setString(1, listType);
            preparedStatement.setInt(2, timesValue);
            preparedStatement.setLong(3, userId);
            preparedStatement.setInt(4, word.getWordId());
            preparedStatement.execute();
            logger.info("Слово успешно переведено в другой словарь пользователя");
        } catch (SQLException e) {
            logger.error("Ошибка смены словаря в БД для пользователя" + e);
            throw new RuntimeException(e);
        }
    }



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

    public static String[] splitMessageText(String text) {
        nullCheck.checkForNull("splitMessageText ", text);
        String[] texts = text.split(" {2}- {2}");
        nullCheck.checkForNull("splitMessageText ", texts[0], texts[1]);
        texts[0] = texts[0].trim();
        texts[1] = texts[1].trim();

        return texts;
    }
}
