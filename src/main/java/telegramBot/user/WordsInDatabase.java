package telegramBot.user;

import dataBase.DatabaseConnection;
import telegramBot.Word;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class WordsInDatabase extends DatabaseConnection {


    static boolean addWord(String word, Long userId) {
        if (word == null || userId == null) {
            throw new IllegalArgumentException("word and userId must not be null");
        }

        Connection connection = getConnection();

        try {
            PreparedStatement ps = connection.prepareStatement("SELECT word_id FROM words WHERE LOWER(english_word) = LOWER(?) OR LOWER(russian_word) = LOWER(?)");
            ps.setString(1, word.toLowerCase());
            ps.setString(2, word.toLowerCase());
            ResultSet rs = ps.executeQuery();
            Set<Integer> wordId = new HashSet<>();
            while (rs.next()) {
                wordId.add(rs.getInt("word_id"));
            }

            if (wordId.size() == 0) {
                List<String> translatorResult = Word.translate(word);
                try {
                    ps = connection.prepareStatement("INSERT INTO words (english_word, russian_word) VALUES (?, ?)");
                    ps.setString(1, translatorResult.get(0));
                    ps.setString(2, translatorResult.get(1));
                    ps.executeUpdate();
                    System.out.println("Слово успешно добавлено в общий словарь");
                    rs = ps.executeQuery();
                    if (rs.next()) {
                        wordId.add(rs.getInt("id"));
                    }
                } catch (SQLException e) {
                    System.out.println("Не удалось добавить слово");
                    e.printStackTrace();
                }
            }

            ps = connection.prepareStatement("SELECT word_id FROM user_word_lists WHERE user_id = ? AND word_id IN (" + String.join(",", Collections.nCopies(wordId.size(), "?")) + ")");
            ps.setLong(1, userId);
            int count = 2;
            for (int i : wordId) {
                ps.setInt(count, i);
                count++;
            }
            rs = ps.executeQuery();
            Set<Integer> userWordId = new HashSet<>();
            while (rs.next()) {
                wordId.remove(rs.getInt("word_id"));
            }

            for (int id : wordId) {
                ps = connection.prepareCall("insert into user_word_lists (user_id, word_id) VALUES (?, ?)");
                ps.setLong(1, userId);
                ps.setInt(2, id);
                ps.executeUpdate();
                System.out.println("Слово успешно добавлено в словарь пользователя.");
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


}
