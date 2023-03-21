package telegramBot;

import Exceptions.TranslationException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.cognitiveservices.speech.*;
import dataBase.DatabaseConnection;
import okhttp3.*;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public record Word(String enWord, String ruWord) implements Serializable {

    private static final Logger logger = Logger.getLogger(Word.class);


    /*Получение случайного слова из БД словаря пользователя.*/
    static @Nullable Word getWord(Long userId) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getWord ", userId);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWord connection ", connection);


        String russianWord = null;
        String englishWord = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT russian_word, english_word FROM words " +
                        "WHERE word_id = " +
                        "(SELECT word_id FROM user_word_lists " +
                        "WHERE user_id = ? AND list_type = " +
                        "(SELECT menu_name FROM user_menu " +
                        "WHERE user_id = ?) " +
                        "ORDER BY RANDOM() LIMIT 1)")) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
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

        if (russianWord != null && englishWord != null)
            return new Word(englishWord, russianWord);
        else return null;
    }

    /*Данный метод при запросе возвращает объект File по адресу которого находится аудио файл с английской озвучкой слова.
     * В первую очередь проверяет среди уже сохраненных слов. Если не найдено отправляет в TTS*/
    public File getVoice() throws Exception {
        File directory = new File("voice");

        if (!directory.isDirectory()) {
            directory.mkdirs();
        }

        File voice = new File("voice/" + enWord() + ".wav");

        if (!voice.exists()) {
            logger.info("Файла произношения не нашлось. Отправка слова в TTS");
            createSpeech(enWord(), voice);
        }

        return voice;
    }

    /*Данный метод принимает String который нужно озвучить и "File" по адресу которого должен находится аудиофайл с
     * озвучкой. Посылает данный текст в Microsoft TTS и полученный результат сохраняет по адресу в объекте File*/
    private void createSpeech(String text, File voice) throws Exception {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("createSpeech ", text, voice);
        // Replace with your own subscription key and region
        String subscriptionKey = "e2c7953181e04a5cb85981e5a309d7f4";
        String serviceRegion = "germanywestcentral";

        try (FileOutputStream fos = new FileOutputStream(voice)) {

            SpeechConfig speechConfig = SpeechConfig.fromSubscription(subscriptionKey, serviceRegion);

            speechConfig.setSpeechSynthesisVoiceName("en-US-JennyNeural");

            SpeechSynthesizer speechSynthesizer = new SpeechSynthesizer(speechConfig);

            if (text.isEmpty()) {
                return;
            }

            // Get the synthesized speech as an audio stream
            SpeechSynthesisResult result = speechSynthesizer.SpeakText(text);

            logger.info("Слово " + text + "result.id :" + result.getResultId() + ", result.resultReason : " + result.getReason()
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

        if (!(word.equals(result.get(0)) || word.equals(result.get(1)))) {
            logger.error("ПРИШЕЛ НЕКОРЕКТНЫЙ ПЕРЕВОД НА СЛОВО '" + word + "'");
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
