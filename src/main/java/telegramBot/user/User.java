package telegramBot.user;

import dataBase.DatabaseConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.telegram.telegrambots.meta.api.objects.Message;
import telegramBot.AllWordBase;
import telegramBot.TelegramApiConnect;
import telegramBot.Word;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

public class User implements Serializable {

    public User() {

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


    public void removeWord(String keyWord) {
        inRepeatingProcess.remove(keyWord.toLowerCase());
        inLeaningProcess.remove(keyWord.toLowerCase());
    }

    public void fromLeaningToRepeat(String key) {
        inRepeatingProcess.put(key.toLowerCase(), inLeaningProcess.get(key.toLowerCase()));
        inLeaningProcess.remove(key.toLowerCase());
    }

    public void fromRepeatToLeaning(String key) {
        inLeaningProcess.put(key.toLowerCase(), inRepeatingProcess.get(key.toLowerCase()));
        inRepeatingProcess.remove(key.toLowerCase());
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

    public static String getUserMenu(Long userId) {
        Connection connection = DatabaseConnection.getConnection();
        String result = null;
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT menu_name FROM user_menu WHERE user_id = ?")) {
            preparedStatement.setLong(1, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            resultSet.next();
            result = resultSet.getString("menu_name");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
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
