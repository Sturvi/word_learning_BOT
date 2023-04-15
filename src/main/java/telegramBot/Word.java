package telegramBot;

import Exceptions.TranslationException;
import Exceptions.WordTypeException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.cognitiveservices.speech.*;
import dataBase.DatabaseConnection;
import okhttp3.*;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class Word implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(Word.class);
    private static final NullCheck nullCheck = () -> LOGGER;
    private final String enWord;
    private final String ruWord;
    private final Integer wordId;
    private final String transcription;

    private Word(String enWord, String ruWord, Integer wordId, String transcription) {
        this.enWord = enWord;
        this.ruWord = ruWord;
        this.wordId = wordId;
        this.transcription = transcription;
    }

    private Word(String enWord, String ruWord, Integer wordId) {
        this.enWord = enWord;
        this.ruWord = ruWord;
        this.wordId = wordId;
        this.transcription = "";
    }

    /**
     * Возвращает транскрипцию слова на английском языке.
     *
     * @return строка с транскрипцией слова
     */
    public String getTranscription() {
        return transcription;
    }

    /**
     * Возвращает идентификатор слова в базе данных.
     *
     * @return идентификатор слова в базе данных
     */
    public Integer getWordId() {
        return wordId;
    }

    /**
     * Возвращает английскую версию слова.
     *
     * @return строка с английским словом
     */
    public String getEnWord() {
        return enWord;
    }

    /**
     * Возвращает русскую версию слова.
     *
     * @return строка с русским словом
     */
    public String getRuWord() {
        return ruWord;
    }

    /**
     * Метод возвращает объект Word из базы данных по заданному идентификатору wordId.
     * Объект содержит переводы на английский и русский языки, а также транскрипцию на английском языке.
     * Если транскрипция отсутствует в базе данных, метод запускает отдельный поток для ее получения.
     *
     * @param wordId идентификатор слова в базе данных
     * @return объект Word с переводами и транскрипцией
     */
    public static Word getWord(Integer wordId) {
        LOGGER.info("Начало создания нового объекта слова по word_id из БД");
        Connection connection = DatabaseConnection.getConnection();
        String russianWord = null;
        String englishWord = null;
        String transcription = null;

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT russian_word, english_word, transcription FROM words " +
                        "WHERE word_id = ?")) {
            ps.setInt(1, wordId);
            ResultSet resultSet = ps.executeQuery();

            if (resultSet.next()) {
                russianWord = resultSet.getString("russian_word");
                englishWord = resultSet.getString("english_word");
                transcription = resultSet.getString("transcription");
            }
            LOGGER.info("Слово получено из БД получены");
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения слова из БД " + e);
            throw new RuntimeException(e);
        }

        if (transcription == null) {
            String finalEnglishWord = englishWord;
            String finalRussianWord = russianWord;
            Runnable runnable = () -> new Word(finalEnglishWord, finalRussianWord, wordId).addTranscription();
            new Thread(runnable).start();
            transcription = "";
        }

        return new Word(englishWord, russianWord, wordId, transcription);
    }

    /**
     * Метод возвращает объект Word, соответствующий английскому или русскому слову, переданному в параметре messageText.
     * Если слово не найдено, метод вернет первый элемент списка слов, возвращаемых методом getWordList.
     *
     * @param messageText строка, содержащая слово для поиска
     * @return объект Word, соответствующий английскому или русскому слову
     */
    public static Word getWord(String messageText) {
        LOGGER.info("Старт метода getWord");
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("getWord", messageText);

        return getWordList(null, messageText).get(0);
    }

    /**
     * Возвращает список объектов Word, соответствующих сообщению пользователя.
     * Если пользователь не равен null, возвращается список слов из пользовательского словаря.
     *
     * @param user        объект BotUser, может быть null
     * @param messageText текст сообщения пользователя
     * @return список объектов Word
     */
    public static ArrayList<Word> getWordList(BotUser user, String messageText) {
        LOGGER.info("Старт метода getWordList");
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("getWordList", messageText);

        // Удаление символов в квадратных скобках и удаление пробелов в начале и конце строки
        messageText = messageText.replaceAll("\\[.*?\\]|^.*?\\n\\n", "").trim();

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWord Connection ", connection);

        // Разбиваем текст сообщения на слова
        ArrayList<String> words = splitMessageText(messageText);

        // Создание SQL-запроса для получения слов из базы данных
        String sql;
        if (user != null) {
            sql = "SELECT w.word_id, w.russian_word, w.english_word, w.transcription FROM words w INNER JOIN user_word_lists uwl ON w.word_id = uwl.word_id WHERE uwl.user_id = ? AND (";
        } else {
            sql = "SELECT word_id, russian_word, english_word, transcription FROM words WHERE ";
        }

        if (words.size() == 1) {
            sql += "LOWER(english_word) = LOWER(?) OR LOWER(russian_word) = LOWER(?)";
        } else {
            sql += "(LOWER(english_word) = LOWER(?) AND LOWER(russian_word) = LOWER(?)) OR " +
                    "(LOWER(english_word) = LOWER(?) AND LOWER(russian_word) = LOWER(?))";
        }

        if (user != null) {
            sql += ")";
        }

        ArrayList<Word> wordList = new ArrayList<>();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            if (user != null) {
                preparedStatement.setLong(1, user.getUserId());
                preparedStatement.setString(2, words.get(0));
                if (words.size() == 1) {
                    preparedStatement.setString(3, words.get(0));
                } else {
                    preparedStatement.setString(3, words.get(1));
                    preparedStatement.setString(4, words.get(1));
                    preparedStatement.setString(5, words.get(0));
                }
            } else {
                preparedStatement.setString(1, words.get(0));
                if (words.size() == 1) {
                    preparedStatement.setString(2, words.get(0));
                } else {
                    preparedStatement.setString(2, words.get(1));
                    preparedStatement.setString(3, words.get(1));
                    preparedStatement.setString(4, words.get(0));
                }
            }

            // Выполнение запроса и обработка результата
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String transcription = resultSet.getString("transcription");
                String englishWord = resultSet.getString("english_word");
                String russianWord = resultSet.getString("russian_word");
                Integer wordId = resultSet.getInt("word_id");

                if (transcription == null) transcription = "";

                wordList.add(new Word(englishWord, russianWord, wordId, transcription));
            }
        } catch (SQLException e) {
            LOGGER.error("ОШИБКА ПРИ ПОЛУЧЕНИИ СЛОВА ИЗ БД " + e);
            throw new RuntimeException(e);
        }

        return wordList;
    }

    /**
     * Метод возвращает множество случайных слов из базы данных, которых еще нет в словаре пользователя.
     *
     * @param user пользователь, для которого нужно получить множество случайных слов
     * @return множество идентификаторов слов
     */
    public static HashSet<Integer> getRandomNewWordSet(BotUser user) {
        LOGGER.info("Старт метода Word.getRandomNewWordSet");

        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("getWord ", user);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWord connection ", connection);

        var wordSet = new HashSet<Integer>();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT word_id " +
                        "FROM words " +
                        "WHERE word_id NOT IN ( " +
                        "    SELECT word_id " +
                        "    FROM user_word_lists " +
                        "    WHERE user_id = ? " +
                        ") " +
                        "ORDER BY RANDOM() " +
                        "LIMIT 10;")) {
            preparedStatement.setLong(1, user.getUserId());

            ResultSet resultSet = preparedStatement.executeQuery();
            LOGGER.info("Запрос на получения листа случайных слов в БД отправлено");

            while (resultSet.next()) {
                wordSet.add(resultSet.getInt("word_id"));
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения листа случайных слов " + e);
            throw new RuntimeException(e);
        }

        return wordSet;
    }

    /**
     * Получение случайного слова из БД словаря пользователя.
     *
     * @param user объект пользователя, для которого необходимо получить случайное слово
     * @return случайное слово из словаря пользователя или null, если слова отсутствуют
     */
    public static @Nullable Word getRandomWordFromUserDictionary(BotUser user) {
        LOGGER.info("Старт метода Word.getRandomWordFromUserDictionary");

        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("getWord ", user);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWord connection ", connection);

        Word word;

        final String SQL_QUERY = "WITH menu AS (" +
                "   SELECT menu_name " +
                "   FROM user_menu " +
                "   WHERE user_id = ? " +
                ") " +
                "SELECT w.word_id " +
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
                "       )) OR " +
                "       ((m.menu_name = 'repetition' OR m.menu_name = 'mixed') AND uwl.list_type = 'learned' AND EXTRACT(day FROM CURRENT_TIMESTAMP - last_repetition_time) >= timer_value) " +
                "   ) " +
                "   ORDER BY RANDOM() " +
                "   LIMIT 1 " +
                ") uwl ON w.word_id = uwl.word_id;";

        try (PreparedStatement ps = connection.prepareStatement(SQL_QUERY)) {
            ps.setLong(1, user.getUserId());
            ps.setLong(2, user.getUserId());
            ResultSet resultSet = ps.executeQuery();

            word = extractWordFromResultSet(resultSet);
            LOGGER.info("Слово получено из БД");
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения слова из БД " + e);
            throw new RuntimeException(e);
        }

        return word;
    }

    /**
     * Извлечение слова из результата выполнения SQL-запроса.
     *
     * @param resultSet результат выполнения SQL-запроса
     * @return слово из результата или null, если слово отсутствует
     * @throws SQLException при ошибке обработки результата запроса
     */
    private static Word extractWordFromResultSet(ResultSet resultSet) throws SQLException {
        Word word = null;
        if (resultSet.next()) {
            int wordId = resultSet.getInt("word_id");
            word = Word.getWord(wordId);
        }
        return word;
    }

    /**
     * Данный метод при запросе возвращает объект File, по адресу которого находится аудио файл с английской озвучкой слова.
     * В первую очередь проверяет среди уже сохраненных слов. Если не найдено, отправляет в TTS.
     *
     * @return объект File, по адресу которого находится аудио файл с английской озвучкой слова
     * @throws Exception в случае ошибки при создании аудиофайла через TTS
     */
    public File getVoice() throws Exception {
        File directory = new File("voice");

        if (!directory.isDirectory()) {
            directory.mkdirs();
        }

        File voice = new File("voice/" + getEnWord() + ".wav");

        if (!voice.exists()) {
            LOGGER.info("Файла произношения не нашлось. Отправка слова в TTS");
            createSpeech();
        }

        return voice;
    }

    /**
     * Данный метод принимает String, который нужно озвучить, и File, по адресу которого должен находиться аудиофайл с
     * озвучкой. Посылает данный текст в Microsoft TTS и полученный результат сохраняет по адресу в объекте File.
     *
     * @throws Exception в случае ошибки при получении произношения из TTS или записи аудиофайла
     */
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

            LOGGER.info("Слово " + getEnWord() + "result.id :" + result.getResultId() + ", result.resultReason : " + result.getReason()
                    + ", result.audioDuration : " + result.getAudioDuration() + ", result.audioLength : " + result.getAudioLength());

            if (result.getAudioData() != null) {
                fos.write(result.getAudioData());
                LOGGER.info("Аудио файл записан в файл word.wav");
            } else {
                LOGGER.error("Ошибка получения произношения из TTS");
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка получения произношения из TTS " + e);
            throw e;
        }
    }

    /**
     * Метод использует Google API для получения перевода введенного слова с английского на русский язык и наоборот.
     * Результат передается в виде списка строк. Первое слово в листе на английском, второе на русском.
     * Если входное слово - null, выбрасывается исключение TranslationException.
     *
     * @param word слово для перевода
     * @return список строк, содержащий переведенное слово: первый элемент - на английском, второй - на русском
     */
    private static ArrayList<String> translate(String word) {
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("translate", word);

        ArrayList<String> resultList = new ArrayList<>();
        googleApiTranslate(word, "en", resultList);
        googleApiTranslate(word, "ru", resultList);

        return resultList;
    }

    /**
     * Метод отправляет запрос на сервис Google Translate для получения перевода слова на заданный язык и
     * добавляет результат в ArrayList результатов. Используется ключ API Google.
     *
     * @param word       слово для перевода
     * @param language   код языка, на который необходимо перевести слово
     * @param resultList список результатов, в который будет добавлен результат перевода
     * @throws RuntimeException если возникла ошибка при получении перевода слова из Google Translate
     */
    private static void googleApiTranslate(String word, String language, ArrayList<String> resultList) {
        String apiKey = Api.getApiKey("google");
        OkHttpClient client = new OkHttpClient();

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType,
                "{\"q\": \"" + word + "\", \"target\": \"" + language + "\"}");
        Request request = new Request.Builder()
                .url("https://translation.googleapis.com/language/translate/v2?key=" + apiKey)
                .post(body)
                .addHeader("Content-type", "application/json")
                .build();
        Response response;
        String jsonString;

        try {
            response = client.newCall(request).execute();
            assert response.body() != null;
            jsonString = response.body().string();
            LOGGER.info("Перевод слова из переводчика удачно получен");
        } catch (IOException e) {
            LOGGER.error("Ошибка получения слова из переводчика. " + e);
            throw new RuntimeException(e);
        }

        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        JsonObject dataObject = jsonObject.get("data").getAsJsonObject();
        JsonArray translations = dataObject.get("translations").getAsJsonArray();
        for (JsonElement translation : translations) {
            resultList.add(translation.getAsJsonObject().get("translatedText").getAsString());
        }
    }

    /**
     * Возвращает контекст для заданного английского слова.
     * Если контекст отсутствует в базе данных, то производит запрос к API и сохраняет контекст в базу данных.
     * Возвращает контекст из базы данных или API.
     *
     * @return контекст для заданного английского слова
     */
    public String getContextOrUsageExamples(String contentType) {
        LOGGER.info("Старт метода getContext");
        String content = null;
        try {
            content = getContentFromDataBase(contentType);
            if (content == null) {
                addContentToDataBase(contentType);
                content = getContentFromDataBase(contentType);
            }
        } catch (RuntimeException e) {
            LOGGER.error("ОШИБКА получения контекста из БД " + e.getMessage());
        }
        return content;
    }

    /**
     * Метод для получения контекста или примера использования из базы данных по английскому слову.
     * В качестве параметра передается тип контента: "context" или "usage_examples".
     * Если контекст или пример использования не найдены в базе данных, возвращается null.
     *
     * @param contentType тип контента для получения: "context" или "usage_examples"
     * @return контекст или пример использования, найденный в базе данных, или null, если не найден
     * @throws WordTypeException если contentType не равно "context" или "usage_examples"
     * @throws RuntimeException  если возникла ошибка при получении контекста или примера использования из базы данных
     */
    private String getContentFromDataBase(String contentType) {
        LOGGER.info("Старт метода Word.getContentFromDataBase");
        Connection connection = DatabaseConnection.getConnection();

        if (!(contentType.equals("context") || contentType.equals("usage_examples")))
            throw new WordTypeException();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT " + contentType + " FROM word_contexts WHERE english_word = ?")) {
            preparedStatement.setString(1, getEnWord());

            ResultSet resultSet = preparedStatement.executeQuery();
            LOGGER.info("resultSet получен");
            if (resultSet.next()) {
                LOGGER.info("Контекст или пример использования получен из БД");
                return resultSet.getString(contentType);
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения контекста или примера использования из БД");
            throw new RuntimeException(e);
        }

        return null;
    }

    /**
     * Этот метод добавляет контекст в базу данных для заданного английского слова.
     *
     * @param contentType тип контента, который нужно добавить в базу данных (context или usage_examples)
     * @throws WordTypeException если contentType не равно "context" или "usage_examples"
     */
    void addContentToDataBase(String contentType) {
        LOGGER.info("Старт метода Word.addContextToDataBase");
        Connection connection = DatabaseConnection.getConnection();
        String content;

        if (!(contentType.equals("context") || contentType.equals("usage_examples")))
            throw new WordTypeException();

        try {
            content = Api.getResponse(getEnWord(), contentType);
        } catch (IOException e) {
            LOGGER.error("Ошибка получения контекста из API " + e);
            throw new RuntimeException(e);
        }

        String sql = "INSERT INTO word_contexts (english_word, " + contentType + ") VALUES (?, ?) " +
                "ON CONFLICT (english_word) DO UPDATE SET " + contentType + " = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, getEnWord());
            preparedStatement.setString(2, content);
            preparedStatement.setString(3, content);

            preparedStatement.execute();
            LOGGER.info("Контекст успешно добавлен в Базу данных");
        } catch (SQLException e) {
            LOGGER.error("Ошибка добавления контекста в Базу данных");
            throw new RuntimeException(e);
        }
    }

    /**
     * Метод добавляет новое слово в словарь пользователя.
     * Если слово уже есть в словаре, метод возвращает сообщение об этом. Если слово не найдено в базе данных,
     * оно добавляется в базу данных и затем добавляется в словарь пользователя.
     *
     * @param wordForAdd слово для добавления
     * @param user       пользователь, для которого нужно добавить слово
     * @return множество идентификаторов слов
     * @throws TranslationException если возникла ошибка при переводе слова
     */
    public static Set<Integer> add(@NotNull String wordForAdd, BotUser user) throws TranslationException {
        nullCheck.checkForNull("add ", wordForAdd, user);

        Set<Integer> wordIdList = new HashSet<>();
        checkNewWordInDB(wordForAdd, wordIdList);

        if (wordIdList.size() == 0) {
            try {
                addNewWordToDBFromTranslator(wordForAdd, wordIdList);
            } catch (SQLException e) {
                LOGGER.error(e);
            }
            for (Integer temp : wordIdList) {
                Word word = Word.getWord(temp);
                Api.moderation(wordForAdd, word, user);
            }
        }

        checkWordInUserDictionary(wordIdList, user.getUserId());

        return wordIdList;
    }

    /**
     * Этот метод удаляет слово из базы данных. Удаляется запись, связанная с данным словом.
     * В случае успешного удаления, выводится сообщение об успешном удалении слова в лог.
     * В случае ошибки, выбрасывается исключение RuntimeException.
     */
    void deleteWordFromDataBase() {
        LOGGER.info("Начало удаления слова " + this);

        String sql = "DELETE FROM words WHERE word_id = ?;";

        Connection connection = DatabaseConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, getWordId());

            preparedStatement.execute();
            LOGGER.info("Слово " + this + " успешно удалено из Базы Данных");

        } catch (SQLException e) {
            LOGGER.error("Ошибка удаления слова " + this + " из Базы данных " + e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Этот метод добавляет транскрипцию английского слова в базу данных.
     * Транскрипция получается с помощью вызова API.
     * Если транскрипция получена успешно, она обновляется в базе данных для соответствующего слова.
     */
    private void addTranscription() {
        LOGGER.info("Старт метода Word.addTranscription");
        String transcription = null;
        try {
            transcription = Api.getResponse(getEnWord(), "transcription");
        } catch (IOException e) {
            LOGGER.error("Word.addTranscription Ошибка получения транскрипции");
        }
        if (transcription == null) {
            LOGGER.error("Word.addTranscription транскрипция вернулась null");
            return;
        }

        if (!transcription.matches("^\\[.*\\]$")) {
            LOGGER.error("Word.addTranscription транскрипция не соответствует регулярному выражению");
            return;
        }

        Connection connection = DatabaseConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "UPDATE words SET transcription = ? WHERE word_id = ?")) {
            preparedStatement.setString(1, transcription);
            preparedStatement.setInt(2, getWordId());

            preparedStatement.executeUpdate();
            LOGGER.info("Word.addTranscription Транскрипция успешно добавлена");
        } catch (SQLException e) {
            LOGGER.error("Word.addTranscription не удалось добавить транскрипцию в Базу данных");
            throw new RuntimeException(e);
        }
    }

    /**
     * Этот метод проверяет наличие слова в базе данных. Если слово найдено в базе данных,
     * его id добавляется в множество wordId. Метод ничего не возвращает, только изменяет переданное ему множество.
     *
     * @param word   слово для поиска в базе данных
     * @param wordId множество, в которое будут добавлены идентификаторы найденных слов
     */
    private static void checkNewWordInDB(String word, Set<Integer> wordId) {
        nullCheck.checkForNull("checkNewWordInDB", word, wordId);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("checkNewWordInDB connection ", connection);

        try {
            PreparedStatement ps = connection.prepareStatement("SELECT word_id FROM words WHERE LOWER(english_word) = LOWER(?) OR LOWER(russian_word) = LOWER(?)");
            ps.setString(1, word.toLowerCase());
            ps.setString(2, word.toLowerCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                wordId.add(rs.getInt("word_id"));
            }
            LOGGER.info("Проверка нового слова в БД прошла успешно");
        } catch (SQLException e) {
            LOGGER.error("Ошибка проверки нового слова в Базе данных " + e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Метод добавляет новое слово, полученное из переводчика, в базу данных.
     * В случае успеха, добавляет идентификатор нового слова в указанное множество wordId.
     * Если слово уже присутствует в базе данных, добавляет его идентификатор в множество wordId.
     * В случае ошибки, выбрасывает исключение TranslationException или SQLException.
     * SQLException может возникнуть, если возникли проблемы с базой данных или если слово уже присутствует в базе данных.
     *
     * @param word   слово для перевода и добавления в базу данных
     * @param wordId множество, в которое будет добавлен идентификатор добавленного слова
     * @throws TranslationException исключение, выбрасываемое при ошибке в процессе перевода
     * @throws SQLException         исключение, выбрасываемое при ошибке взаимодействия с базой данных, включая случай, когда слово уже присутствует в базе данных
     */
    public static void addNewWordToDBFromTranslator(String word, Set<Integer> wordId) throws TranslationException, SQLException {
        nullCheck.checkForNull("addNewWordToDBFromTranslator ", word, wordId);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("addNewWordToDBFromTranslator connection ", connection);

        // Получение перевода слова
        var translatorResult = Word.translate(word);

        // Проверка корректности перевода
        if (!word.equalsIgnoreCase(translatorResult.get(0)) && !word.equalsIgnoreCase(translatorResult.get(1))) {
            LOGGER.error("Cлово " + word + " вернулось из словаря неправильно. оба перевода не совпадают");
            throw new TranslationException();
        }

        // Форматирование перевода с сохранением регистра
        translatorResult.set(0, translatorResult.get(0).substring(0, 1).toUpperCase() + translatorResult.get(0).substring(1).toLowerCase());
        translatorResult.set(1, translatorResult.get(1).substring(0, 1).toUpperCase() + translatorResult.get(1).substring(1).toLowerCase());

        try {
            // Вставка нового слова в базу данных
            PreparedStatement ps = connection.prepareStatement("INSERT INTO words (english_word, russian_word) VALUES (?, ?)");
            ps.setString(1, translatorResult.get(0));
            ps.setString(2, translatorResult.get(1));
            ps.executeUpdate();
            LOGGER.info("Слово успешно добавлено в общий словарь");

            // Получение идентификатора добавленного слова
            ps = connection.prepareStatement("SELECT lastval()");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                wordId.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка добавления в общий словарь " + e);
            wordId.add(Word.getWord(translatorResult.get(0) + "  -  " + translatorResult.get(1)).getWordId());
            throw e;
        }
    }

    /**
     * Проверяет, есть ли слова из набора в словаре пользователя.
     * Если слово уже есть в словаре, оно удаляется из набора.
     *
     * @param wordId Набор идентификаторов слов для проверки.
     * @param userId Идентификатор пользователя.
     */
    private static void checkWordInUserDictionary(Set<Integer> wordId, Long userId) {
        nullCheck.checkForNull("checkWordInUserDictionary", wordId, userId);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("checkWordInUserDictionary connection ", connection);

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
            LOGGER.info("Поиск слова в словаре пользователя прошла успешно.");
        } catch (SQLException e) {
            LOGGER.error("Ошибка поиска слова в словаре пользователя  " + e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Метод добавляет новые слова в словарь пользователя.
     * Он принимает набор идентификаторов слов и идентификатор пользователя, для которого нужно добавить слова.
     * В цикле происходит добавление каждого слова в таблицу пользовательских слов.
     *
     * @param user пользователь, для которого нужно добавить слова
     */
    public void addNewWordsToUserDictionary(BotUser user) {
        nullCheck.checkForNull("addNewWordsToUserDictionary ", user);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("addNewWordsToUserDictionary connection ", connection);
        try {
            PreparedStatement ps = connection.prepareCall("insert into user_word_lists (user_id, word_id) VALUES (?, ?)");
            ps.setLong(1, user.getUserId());
            ps.setInt(2, getWordId());
            ps.executeUpdate();
            LOGGER.info("Слово успешно добавлено в словарь пользователя.");
        } catch (SQLException e) {
            LOGGER.error("Не удалось добавить слово в словарь пользователя " + e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Возвращает список слов определенного типа, связанных с пользователем.
     *
     * @param user     пользователь, для которого нужно получить список слов
     * @param listType тип списка слов ("learning" или "repetition")
     * @return список строк с информацией о словах или пустой список, если список пуст
     */
    public static ArrayList<String> fetchUserWords(BotUser user, String listType) {
        nullCheck.checkForNull("getWordList ", user, listType);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWordList Connection ", connection);
        var messagesList = new ArrayList<String>();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT w.word_id, uwl.timer_value " +
                        "FROM words w " +
                        "JOIN user_word_lists uwl ON w.word_id = uwl.word_id " +
                        "WHERE uwl.user_id = ? AND uwl.list_type = ?"
        )) {
            preparedStatement.setLong(1, user.getUserId());
            preparedStatement.setString(2, listType);
            ResultSet resultSet = preparedStatement.executeQuery();

            switch (listType.toLowerCase()) {
                case "learning" -> messagesList = processLearningList(resultSet);
                case "repetition" -> messagesList = processRepetitionList(resultSet);
            }
        } catch (SQLException e) {
            LOGGER.error("Получение листа из БД", e);
            throw new RuntimeException(e);
        }

        return messagesList.isEmpty() ? new ArrayList<>() : messagesList;
    }

    /**
     * Обрабатывает список слов на изучении.
     *
     * @param resultSet результат выполнения SQL-запроса, содержащий слова на изучении
     * @return список строк с информацией о словах на изучении
     * @throws SQLException если возникает ошибка при обработке результата SQL-запроса
     */
    private static @NotNull ArrayList<String> processLearningList(@NotNull ResultSet resultSet) throws SQLException {
        ArrayList<String> messagesList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Список слов на изучении:\n\n");
        while (resultSet.next()) {
            Integer wordId = resultSet.getInt("word_id");
            Word word = Word.getWord(wordId);
            if (stringBuilder.length() + word.toString().length() > 4000) {
                messagesList.add(stringBuilder.toString().trim());
                stringBuilder = new StringBuilder();
            }
            stringBuilder.append(word.toStringWithTranscription()).append("\n");
        }
        messagesList.add(stringBuilder.toString().trim());
        return messagesList;
    }

    /**
     * Обрабатывает список слов на повторении.
     *
     * @param resultSet результат выполнения SQL-запроса, содержащий слова на повторении
     * @return список строк с информацией о словах на повторении, сгруппированных по уровням
     * @throws SQLException если возникает ошибка при обработке результата SQL-запроса
     */
    private static @NotNull ArrayList<String> processRepetitionList(@NotNull ResultSet resultSet) throws SQLException {
        ArrayList<String> messagesList = new ArrayList<>();
        Map<Integer, ArrayList<Word>> repetitionWords = new HashMap<>();
        while (resultSet.next()) {
            Integer timerValue = resultSet.getInt("timer_value");

            Integer wordId = resultSet.getInt("word_id");
            Word word = Word.getWord(wordId);

            if (!repetitionWords.containsKey(timerValue))
                repetitionWords.put(timerValue, new ArrayList<>());
            repetitionWords.get(timerValue).add(word);
        }

        for (int i = 1; i < 7; i++) {
            StringBuilder stringBuilder = new StringBuilder();
            if (repetitionWords.containsKey(i)) {
                stringBuilder.append("Слова на повторении ").append(i).append(" уровня \n\n");
                for (Word word : repetitionWords.get(i)) {
                    if (stringBuilder.length() + word.toString().length() > 4000) {
                        messagesList.add(stringBuilder.toString().trim());
                        stringBuilder = new StringBuilder();
                    }
                    stringBuilder.append(word.toStringWithTranscription()).append("\n");
                }
                messagesList.add(stringBuilder.toString().trim());
            }
        }

        return messagesList;
    }

    /**
     * Разбивает текст на части по разделителю "  -  ".
     *
     * @param text Текст для разбиения.
     * @return Список строк с частями текста.
     */
    private static ArrayList<String> splitMessageText(String text) {
        nullCheck.checkForNull("splitMessageText ", text);
        String[] texts = text.split(" {2}- {2}");
        ArrayList<String> result = new ArrayList<>();
        for (String temp : texts) {
            result.add(temp.trim());
        }

        return result;
    }

    /**
     * Метод deleteWordFromUserList удаляет слово из списка слов пользователя.
     * Проверяет наличие необходимых параметров, устанавливает соединение с базой данных,
     * выполняет SQL-запрос на удаление слова из списка и логирует результат.
     * Если удаление не удалось, возвращает false.
     *
     * @param user объект BotUser, содержащий информацию о пользователе
     */
    public void deleteWordFromUserList(BotUser user) {
        nullCheck.checkForNull("removeWord", user);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("removeWord Connection", connection);
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "DELETE FROM user_word_lists where user_id = ? AND word_id = ?;")) {
            preparedStatement.setLong(1, user.getUserId());
            preparedStatement.setInt(2, getWordId());

            preparedStatement.execute();
            LOGGER.info("Слово успешно удалено из словаря");
        } catch (SQLException e) {
            LOGGER.error("Не удалось удалить слово из словаря" + e);
        }
    }

    /**
     * Возвращает строку с информацией об объекте в формате: "EnWord Transcription - RuWord" или "RuWord - EnWord Transcription".
     * Формат выбирается случайным образом.
     *
     * @return Строка с информацией об объекте.
     */
    public String toStringRandomWithTranscription(BotUser user) {
        int repetitions = user.getRepetitionsCount(this);

        String result = "Слово из словаря \"";

        if (repetitions == 0) {
            result += "Изучаемые слова\"\n\n";
        } else if (repetitions < 7) {
            result += "Слова на повторении " + repetitions + " уровня\"\n\n";
        } else {
            result += "Изученное слово\"\n\n";
        }

        boolean random = new Random().nextBoolean();
        String tempEnWord = getEnWord().substring(0, 1).toUpperCase() + getEnWord().substring(1) + "   " + getTranscription();
        String tempRuWord = getRuWord().substring(0, 1).toUpperCase() + getRuWord().substring(1);

        result += random ? tempEnWord + "  -  " + " <span class='tg-spoiler'> " + tempRuWord + "</span>" : tempRuWord + "  -  " + " <span class='tg-spoiler'> " + tempEnWord + "</span>";

        return result;
    }

    @Override
    public String toString() {
        return getEnWord().substring(0, 1).toUpperCase() + getEnWord().substring(1) + "  -  " +
                getRuWord().substring(0, 1).toUpperCase() + getRuWord().substring(1);
    }

    public String toStringWithTranscription() {
        return getEnWord().substring(0, 1).toUpperCase() + getEnWord().substring(1) + "   " + getTranscription() + "  -  " +
                getRuWord().substring(0, 1).toUpperCase() + getRuWord().substring(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Word word = (Word) o;
        return Objects.equals(wordId, word.wordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wordId);
    }
}