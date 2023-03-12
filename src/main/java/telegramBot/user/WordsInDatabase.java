package telegramBot.user;

import dataBase.DatabaseConnection;
import telegramBot.Word;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class WordsInDatabase extends DatabaseConnection {

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

    static void addNewWordToDBFromTranslator (String word, Set<Integer> wordId) {
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
}
