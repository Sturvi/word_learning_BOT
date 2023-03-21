package telegramBot.user;

import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class BotsUser implements Serializable {

    private static final Logger logger = Logger.getLogger(BotsUser.class);


    private BotsUser() {

    }

    public static @Nullable String getWordList(Long userId, String list_type) {
        Connection connection = DatabaseConnection.getConnection();
        StringBuilder stringBuilder = new StringBuilder();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT russian_word, english_word FROM words WHERE word_id IN (" +
                        "SELECT word_id FROM user_word_list WHERE user_id = ? AND list_type = ?)"
        )) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, list_type);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String russianWord = resultSet.getString("russian_word");
                String englishWord = resultSet.getString("english_word");
                stringBuilder.append(englishWord).append("  -  ").append(russianWord).append("\n");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        String result = stringBuilder.toString().trim();
        return result.isEmpty() ? null : result;
    }


    public static void removeWord(Long userId, @NotNull String text) {
        String[] word = text.split(" - ");
        word[0] = word[0].trim();
        word[1] = word[1].trim();

        Connection connection = DatabaseConnection.getConnection();
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "DELETE FROM user_word_list where user_id = ? AND word_id = (" +
                        "SELECT word_id FROM words WHERE" +
                        "(russian_word = ? AND english_word = ?) OR " +
                        "(english_word = ? AND russian word = ?))")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, word[0]);
            preparedStatement.setString(3, word[1]);
            preparedStatement.setString(4, word[0]);
            preparedStatement.setString(5, word[1]);
            preparedStatement.execute();
            System.out.println("Слово успешно удалено из словаря");
        } catch (SQLException e) {
            System.out.println("Не удалось удалить слово из словаря");
            throw new RuntimeException(e);
        }
    }

    public static void changeWordListType(Long userId, String listType, @NotNull String textFromMessage) {
        Connection connection = DatabaseConnection.getConnection();

        if (connection == null) logger.error("changeWordListType Ошибка подключения к БД. connection вернулся null");


        String[] word = textFromMessage.split("  -  ");
        word[0] = word[0].trim();
        word[1] = word[1].trim();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "UPDATE user_word_lists " +
                        "SET list_type = ? " +
                        "WHERE user_id = ? AND word_id = " +
                        "(SELECT word_id FROM words " +
                        "WHERE (russian_word = ? AND english_word = ?) " +
                        "OR (english_word = ? AND russian_word = ?))")) {
            preparedStatement.setString(1, listType);
            preparedStatement.setLong(2, userId);
            preparedStatement.setString(3, word[0]);
            preparedStatement.setString(4, word[1]);
            preparedStatement.setString(5, word[0]);
            preparedStatement.setString(6, word[1]);
            preparedStatement.execute();
            logger.info("Слово успешно переведено в другой словарь пользователя");
        } catch (SQLException e) {
            logger.error("Ошибка смены словаря в БД для пользователя" + e);
            throw new RuntimeException(e);
        }
    }

    public static String add(@NotNull String word, Long userId) {
        if (word.length() > 1 || word.equalsIgnoreCase("i")) {
            Set<Integer> wordId = new HashSet<>();
            WordsInDatabase.checkNewWordInDB(word, wordId);

            if (wordId.size() == 0) {
                WordsInDatabase.addNewWordToDBFromTranslator(word, wordId);
            }

            WordsInDatabase.checkWordInUserDictionary(wordId, userId);

            if (wordId.isEmpty()) {
                return "Данное слово (или словосочетание) уже находятся в твоем словаре";
            }

            WordsInDatabase.addNewWordsToUserDictionary(wordId, userId);
            return "Слово (или словосочетание) успешно добавлено в твой словарь";
        } else {
            return "Слово должно состоять из 2 и более букв";
        }
    }

    public static @Nullable String getUserMenu(Long userId) {
        Connection connection = DatabaseConnection.getConnection();

        if (connection == null) logger.error("getUserMenu Ошибка подключения к БД. connection вернулся null");

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT menu_name FROM user_menu WHERE user_id = ?")) {
            preparedStatement.setLong(1, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next())
                return resultSet.getString("menu_name");
        } catch (SQLException e) {
            logger.error("Не удалось получить меню из БД" + e);
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void setMenu(Long userId, String menuName) {
        Connection connection = DatabaseConnection.getConnection();

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE  user_menu SET menu_name = ? WHERE user_id = ?")) {
            ps.setString(1, menuName);
            ps.setLong(2, userId);
            ps.executeUpdate();
            System.out.println("User " + userId + " menu selected");
        } catch (SQLException e) {
            System.err.println("Error inserting user menu: " + e.getMessage());
        }
    }

    public static @NotNull String getStatistic(Long userId) {
        Connection connection = DatabaseConnection.getConnection();
        Integer learningCount = null;
        Integer repetitionCount = null;
        Integer learnedCount = null;

        try (PreparedStatement preparedStatement = connection.prepareStatement("" +
                "SELECT " +
                "(SELECT COUNT(word_id) FROM user_word_list WHERE user_id = ? AND list_type = 'learning') AS learning_count," +
                "(SELECT COUNT(word_id) FROM user_word_list WHERE user_id = ? AND list_type = 'repetition') AS repetition_count," +
                "(SELECT COUNT(word_id) FROM user_word_list WHERE user_id = ? AND list_type = 'learned') AS learned_count;")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setLong(2, userId);
            preparedStatement.setLong(3, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                learningCount = resultSet.getInt("learning_count");
                repetitionCount = resultSet.getInt("repetition_count");
                learnedCount = resultSet.getInt("learned_count");
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return "Изученные слова: " + learnedCount + "\n" +
                "Слова на повторении: " + repetitionCount + "\n" +
                "Слова на изучении: " + learningCount;
    }

    public static class IncorrectMenuSelectionException extends Exception {
    }
}
