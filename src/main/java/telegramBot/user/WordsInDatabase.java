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

public class WordsInDatabase extends DatabaseConnection {

    private static final Logger logger = Logger.getLogger(WordsInDatabase.class);


    static void checkNewWordInDB(String word, Set<Integer> wordId) {
        Connection connection = getConnection();

        try {
            PreparedStatement ps = connection.prepareStatement("SELECT word_id FROM words WHERE LOWER(english_word) = LOWER(?) OR LOWER(russian_word) = LOWER(?)");
            ps.setString(1, word.toLowerCase());
            ps.setString(2, word.toLowerCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                wordId.add(rs.getInt("word_id"));
            }
        } catch (SQLException e) {
            System.out.println("Ошибка проверки нового слова в Базе данных");
            throw new RuntimeException(e);
        }
    }

    static void addNewWordToDBFromTranslator (String word, Set<Integer> wordId) throws TranslationException {
        Connection connection = getConnection();
        List<String> translatorResult = Word.translate(word);
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO words (english_word, russian_word) VALUES (?, ?)");
            ps.setString(1, translatorResult.get(0));
            ps.setString(2, translatorResult.get(1));
            ps.executeUpdate();
            System.out.println("Слово успешно добавлено в общий словарь");
            ps = connection.prepareStatement("SELECT lastval()");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                wordId.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            System.out.println("Не удалось добавить слово");
            e.printStackTrace();
        }
    }

    static void checkWordInUserDictionary (Set<Integer> wordId, Long userId){
        Connection connection = getConnection();
        try {
            String commaSeparatedPlaceholders = String.join(",", Collections.nCopies(wordId.size(), "?"));
            PreparedStatement ps = connection.prepareStatement("SELECT word_id FROM user_word_lists WHERE user_id = ? AND word_id IN (" + commaSeparatedPlaceholders + ")");
            ps.setLong(1, userId);
            int count = 2;
            for (int i : wordId) {
                ps.setInt(count, i);
                count++;
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                wordId.remove(rs.getInt("word_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    static void addNewWordsToUserDictionary (Set<Integer> wordId, Long userId){
        Connection connection = DatabaseConnection.getConnection();
        try{
            for (int id : wordId) {
                PreparedStatement ps = connection.prepareCall("insert into user_word_lists (user_id, word_id) VALUES (?, ?)");
                ps.setLong(1, userId);
                ps.setInt(2, id);
                ps.executeUpdate();
                System.out.println("Слово успешно добавлено в словарь пользователя.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static @Nullable String getWordList(Long userId, String list_type) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getWordList ", userId, list_type);
        Connection connection = getConnection();
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
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("removeWord", userId, text);

        String[] word = text.split(" - ");
        nullCheck.checkForNull("removeWord word ", word[0], word[1]);
        word[0] = word[0].trim();
        word[1] = word[1].trim();

        Connection connection = getConnection();
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

    public static void changeWordListType(Long userId, String listType, @NotNull String textFromMessage) {
        try {
            Connection connection = getConnection();

            if (connection == null) logger.error("changeWordListType Ошибка подключения к БД. connection вернулся null");


            String[] word = textFromMessage.split(" {2}- {2}");
            word[0] = word[0].trim();
            word[1] = word[1].trim();

            try {
                assert connection != null;
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
                }
            } catch (SQLException e) {
                logger.error("Ошибка смены словаря в БД для пользователя" + e);
                throw new RuntimeException(e);
            }
        } catch (Exception e){
            logger.error("changeWordListType Неизвестная ошибка " + e);
            throw new RuntimeException(e);
        }
    }

    public static String add(@NotNull String word, Long userId) throws TranslationException {
        if (word.length() > 1 || word.equalsIgnoreCase("i")) {
            Set<Integer> wordId = new HashSet<>();
            checkNewWordInDB(word, wordId);

            if (wordId.size() == 0) {
                addNewWordToDBFromTranslator(word, wordId);
            }

            checkWordInUserDictionary(wordId, userId);

            if (wordId.isEmpty()) {
                return "Данное слово (или словосочетание) уже находятся в твоем словаре";
            }

            addNewWordsToUserDictionary(wordId, userId);
            return "Слово (или словосочетание) успешно добавлено в твой словарь";
        } else {
            return "Слово должно состоять из 2 и более букв";
        }
    }
}
