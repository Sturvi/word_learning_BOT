package telegramBot;

import Exceptions.TranslationException;
import Exceptions.WordNotFountException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.cognitiveservices.speech.*;
import dataBase.DatabaseConnection;
import okhttp3.*;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import telegramBot.user.WordsInDatabase;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class Word implements Serializable {

    private static final Logger logger = Logger.getLogger(Word.class);
    private final String enWord;
    private final String ruWord;
    private final Integer wordId;

    private Word(String enWord, String ruWord, Integer wordId) {
        this.enWord = enWord;
        this.ruWord = ruWord;
        this.wordId = wordId;
    }

    public Integer getWordId() {
        return wordId;
    }

    public String getEnWord() {
        return enWord;
    }

    public String getRuWord() {
        return ruWord;
    }

    public static Word getWord(Integer wordId) {
        logger.info("Начало создания нового объекта слова по word_id из БД");
        Connection connection = DatabaseConnection.getConnection();
        String russianWord = null;
        String englishWord = null;

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT russian_word, english_word FROM words " +
                        "WHERE word_id = ?")) {
            ps.setInt(1, wordId);
            ResultSet resultSet = ps.executeQuery();

            if (resultSet.next()) {
                russianWord = resultSet.getString("russian_word");
                englishWord = resultSet.getString("english_word");
            }
            logger.info("Слово получено из БД получены");
        } catch (SQLException e) {
            logger.error("Ошибка получения слова из БД " + e);
            throw new RuntimeException(e);
        }

        return new Word(englishWord, russianWord, wordId);
    }

    public static Word getWord(String messageText) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getWord", messageText);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWord Connection ", connection);

        String[] words = WordsInDatabase.splitMessageText(messageText);

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT word_id, russian_word, english_word FROM words " +
                        "WHERE (english_word = ? AND russian_word = ?)" +
                        "OR (english_word = ? AND russian_word = ?)")){
            preparedStatement.setString(1, words[0]);
            preparedStatement.setString(2, words[1]);
            preparedStatement.setString(3, words[1]);
            preparedStatement.setString(4, words[0]);

            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()){
                logger.info("Объект слова успешно создан");
                return new Word(resultSet.getString("english_word"),
                        resultSet.getString("russian_word"),
                        resultSet.getInt("word_id"));
            } else
                throw new WordNotFountException();
        } catch (SQLException | WordNotFountException e) {
            logger.error("ОШИБКА ПРИ ПОЛУЧЕНИИ СЛОВА ИЗ БД " + e);
            throw new RuntimeException(e);
        }
    }

    /*Получение случайного слова из БД словаря пользователя.*/
    public static @Nullable Word getRandomWord(Long userId) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getWord ", userId);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWord connection ", connection);


        String russianWord = null;
        String englishWord = null;
        Integer wordId = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "WITH menu AS (" +
                        "   SELECT menu_name " +
                        "   FROM user_menu " +
                        "   WHERE user_id = ? " +
                        ") " +
                        "SELECT w.word_id, w.russian_word, w.english_word " +
                        "FROM words w " +
                        "JOIN ( " +
                        "   SELECT word_id " +
                        "   FROM user_word_lists uwl, menu m " +
                        "   WHERE uwl.user_id = ? AND ( " +
                        "       (m.menu_name = 'learning' AND uwl.list_type = 'learning') OR " +
                        "       (m.menu_name = 'repetition' AND uwl.list_type = 'repetition' AND EXTRACT(day FROM CURRENT_TIMESTAMP - last_repetition_time) >= timer_value) OR " +
                        "       (m.menu_name = 'mixed' AND ( " +
                        "           (uwl.list_type = 'learning') OR  " +
                        "           (uwl.list_type = 'repetition' AND EXTRACT(day FROM CURRENT_TIMESTAMP - last_repetition_time) >= timer_value) " +
                        "       )) " +
                        "   ) " +
                        "   ORDER BY RANDOM() " +
                        "   LIMIT 1 " +
                        ") uwl ON w.word_id = uwl.word_id;")) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ResultSet resultSet = ps.executeQuery();

            if (resultSet.next()) {
                russianWord = resultSet.getString("russian_word");
                englishWord = resultSet.getString("english_word");
                wordId = resultSet.getInt("word_id");
            }
            logger.info("Слово получено из БД получены");
        } catch (SQLException e) {
            logger.error("Ошибка получения слова из БД " + e);
            throw new RuntimeException(e);
        }

        if (russianWord != null && englishWord != null)
            return new Word(englishWord, russianWord, wordId);
        else return null;
    }

    /*Данный метод при запросе возвращает объект File по адресу которого находится аудио файл с английской озвучкой слова.
     * В первую очередь проверяет среди уже сохраненных слов. Если не найдено отправляет в TTS*/
    public File getVoice() throws Exception {
        File directory = new File("voice");

        if (!directory.isDirectory()) {
            directory.mkdirs();
        }

        File voice = new File("voice/" + getEnWord() + ".wav");

        if (!voice.exists()) {
            logger.info("Файла произношения не нашлось. Отправка слова в TTS");
            createSpeech();
        }

        return voice;
    }

    /*Данный метод принимает String который нужно озвучить и "File" по адресу которого должен находится аудиофайл с
     * озвучкой. Посылает данный текст в Microsoft TTS и полученный результат сохраняет по адресу в объекте File*/
    private void createSpeech() throws Exception {
        File voice = new File("voice/" + getEnWord() + ".wav");

        // Replace with your own subscription key and region
        String subscriptionKey = "e2c7953181e04a5cb85981e5a309d7f4";
        String serviceRegion = "germanywestcentral";

        try (FileOutputStream fos = new FileOutputStream(voice)) {

            SpeechConfig speechConfig = SpeechConfig.fromSubscription(subscriptionKey, serviceRegion);

            speechConfig.setSpeechSynthesisVoiceName("en-US-JennyNeural");

            SpeechSynthesizer speechSynthesizer = new SpeechSynthesizer(speechConfig);

            // Get the synthesized speech as an audio stream
            SpeechSynthesisResult result = speechSynthesizer.SpeakText(getEnWord());

            logger.info("Слово " + getEnWord() + "result.id :" + result.getResultId() + ", result.resultReason : " + result.getReason()
                    + ", result.audioDuration : " + result.getAudioDuration() + ", result.audioLength : " + result.getAudioLength());

            if (result.getAudioData() != null) {
                fos.write(result.getAudioData());
                logger.info("Аудио файл записан в файл word.wav");
            } else {
                logger.error("Ошибка получения произношения из TTS");
            }
        } catch (Exception e) {
            logger.error("Ошибка получения произношения из TTS " + e);
            throw e;
        }
    }

    /*Данный метод принимает String и отправляет его в Microsoft TranslatorAPi получает перевод
    и возвращает его в виде ArrayList, где первый элемент это слово на английском, второй элемент на русском*/
    public static ArrayList<String> translate(String word) throws TranslationException {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("translate", word);
        String key = "9a7b89f2526247049ab6ec3980ae56a8";
        String location = "germanywestcentral";
        OkHttpClient client = new OkHttpClient();

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType,
                "[{\"Text\": \"" + word + "\"}]");
        Request request = new Request.Builder()
                .url("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=en&to=ru")
                .post(body)
                .addHeader("Ocp-Apim-Subscription-Key", key)
                // location required if you're using a multi-service or regional (not global) resource.
                .addHeader("Ocp-Apim-Subscription-Region", location)
                .addHeader("Content-type", "application/json")
                .build();
        Response response;
        String jsonString;

        try {
            response = client.newCall(request).execute();
            assert response.body() != null;
            jsonString = response.body().string();
            logger.info("Перевод слова из переводчика удачно получен");
        } catch (IOException e) {
            logger.error("Ошибка получения слова из переводчика. " + e);
            throw new RuntimeException(e);
        }

        ArrayList<String> result = new ArrayList<>();

        JsonParser jsonParser = new JsonParser();
        JsonArray jsonArray = jsonParser.parse(jsonString).getAsJsonArray();
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();

        JsonArray translations = jsonObject.get("translations").getAsJsonArray();
        for (JsonElement translation : translations) {
            JsonObject translationObject = translation.getAsJsonObject();
            result.add(translationObject.get("text").getAsString());
        }

        if (!(word.equalsIgnoreCase(result.get(0)) || word.equalsIgnoreCase(result.get(1)))) {
            logger.error("ПРИШЕЛ НЕКОРЕКТНЫЙ ПЕРЕВОД НА СЛОВО `" + word + "`");
            throw new TranslationException();
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Word word = (Word) o;
        return enWord.equals(word.enWord) && ruWord.equals(word.ruWord);
    }
}