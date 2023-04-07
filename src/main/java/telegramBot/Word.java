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

    private static final Logger logger = Logger.getLogger(Word.class);
    private static final NullCheck nullCheck = () -> logger;
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

    public String getTranscription() {
        return transcription;
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

    /*Метод получает объект слова из БД по заданному id, содержащему перевод на английский и русский языки,
    а также транскрипцию на английском языке. Если транскрипция отсутствует в БД, запускается отдельный поток
    для ее получения.*/
    public static Word getWord(Integer wordId) {
        logger.info("Начало создания нового объекта слова по word_id из БД");
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
            logger.info("Слово получено из БД получены");
        } catch (SQLException e) {
            logger.error("Ошибка получения слова из БД " + e);
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

    public static Word getWord(String messageText) {
        logger.info("Старт метода getWord");
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getWord", messageText);

        return getWordList(messageText).get(0);
    }

    public static ArrayList<Word> getWordList(String messageText) {
        logger.info("Старт метода getWordList");
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getWordList", messageText);

        messageText = messageText.replaceAll("\\[.*?\\]", "").trim();

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWord Connection ", connection);

        ArrayList<String> words = splitMessageText(messageText);

        String sql;
        if (words.size() == 1)
            sql = "SELECT word_id, russian_word, english_word, transcription FROM words " +
                    "WHERE LOWER(english_word) = LOWER(?) OR LOWER(russian_word) = LOWER(?)";
        else
            sql = "SELECT word_id, russian_word, english_word, transcription FROM words " +
                    "WHERE (LOWER(english_word) = LOWER(?) AND LOWER(russian_word) = LOWER(?)) " +
                    "OR (LOWER(english_word) = LOWER(?) AND LOWER(russian_word) = LOWER(?))";

        ArrayList<Word> wordList = new ArrayList<>();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, words.get(0));
            if (words.size() == 1)
                preparedStatement.setString(2, words.get(0));
            if (words.size() > 1) {
                preparedStatement.setString(2, words.get(1));
                preparedStatement.setString(3, words.get(1));
                preparedStatement.setString(4, words.get(0));
            }

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String transcription = resultSet.getString("transcription");
                String englishWord = resultSet.getString("english_word");
                String russianWord = resultSet.getString("russian_word");
                Integer wordId = resultSet.getInt("word_id");

                if (transcription == null) {
                    Runnable runnable = () -> new Word(englishWord, russianWord, wordId).addTranscription();
                    new Thread(runnable).start();
                    transcription = "";
                }

                wordList.add(new Word(englishWord, russianWord, wordId, transcription));
            }
        } catch (SQLException e) {
            logger.error("ОШИБКА ПРИ ПОЛУЧЕНИИ СЛОВА ИЗ БД " + e);
            throw new RuntimeException(e);
        }

        return wordList;
    }

    public static HashSet<Integer> getRandomNewWordSet(Long userId) {
        logger.info("Старт метода Word.getRandomNewWordSet");

        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getWord ", userId);

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
            preparedStatement.setLong(1, userId);

            ResultSet resultSet = preparedStatement.executeQuery();
            logger.info("Запрос на получения листа случайных слов в БД отправлено");

            while (resultSet.next()) {
                wordSet.add(resultSet.getInt("word_id"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения листа случайных слов " + e);
            throw new RuntimeException(e);
        }

        return wordSet;
    }

    /**
     * Получение случайного слова из БД словаря пользователя.
     *
     * @param userId идентификатор пользователя, для которого необходимо получить случайное слово
     * @return случайное слово из словаря пользователя или null, если слова отсутствуют
     */
    public static @Nullable Word getRandomWordFromUserDictionary(Long userId) {
        logger.info("Старт метода Word.getRandomWordFromUserDictionary");

        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getWord ", userId);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWord connection ", connection);

        int wordId;
        Word word = null;

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
                "       ((m.menu_name = 'repetition' OR m.menu_name = 'mixed') AND uwl.list_type = 'learned' AND EXTRACT(day FROM CURRENT_TIMESTAMP - last_repetition_time) >= 10) " +
                "   ) " +
                "   ORDER BY RANDOM() " +
                "   LIMIT 1 " +
                ") uwl ON w.word_id = uwl.word_id;";

        try (PreparedStatement ps = connection.prepareStatement(SQL_QUERY)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ResultSet resultSet = ps.executeQuery();

            word = extractWordFromResultSet(resultSet);
            logger.info("Слово получено из БД");
        } catch (SQLException e) {
            logger.error("Ошибка получения слова из БД " + e);
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

    /*Метод использует Google API для получения перевода введенного слова с английского на русский язык и наоборот.
    Результат передается в виде списка строк. Первое слово в листе на английском второе на русском.
    Если входное слово - null, выбрасывается исключение TranslationException.*/
    private static ArrayList<String> translate(String word) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("translate", word);

        ArrayList<String> resultList = new ArrayList<>();
        googleApiTranslate(word, "en", resultList);
        googleApiTranslate(word, "ru", resultList);

        return resultList;
    }

    /*Метод отправляет запрос на сервис Google Translate для получения перевода слова на заданный язык и
    добавляет результат в ArrayList результатов. Используется ключ API Google.*/
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
            logger.info("Перевод слова из переводчика удачно получен");
        } catch (IOException e) {
            logger.error("Ошибка получения слова из переводчика. " + e);
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
        logger.info("Старт метода getContext");
        String content = null;
        try {
            content = getContentFromDataBase(contentType);
            if (content == null) {
                addContentToDataBase(contentType);
                content = getContentFromDataBase(contentType);
            }
        } catch (RuntimeException e) {
            logger.error("ОШИБКА получения контекста из БД " + e.getMessage());
        }
        return content;
    }

    /* Метод для получения контекста из БД по английскому слову. Если контекст не найден в БД, возвращается null.*/
    private String getContentFromDataBase(String contentType) {
        logger.info("Старт метода Word.getContentFromDataBase");
        Connection connection = DatabaseConnection.getConnection();

        if (!(contentType.equals("context") || contentType.equals("usage_examples")))
            throw new WordTypeException();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT " + contentType + " FROM word_contexts WHERE english_word = ?")) {
            preparedStatement.setString(1, getEnWord());

            ResultSet resultSet = preparedStatement.executeQuery();
            logger.info("resultSet получен");
            if (resultSet.next()) {
                logger.info("Контекст или пример использования получен из БД");
                return resultSet.getString(contentType);
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения контекста или примера использования из БД");
            throw new RuntimeException(e);
        }

        return null;
    }

    /*Этот метод добавляет контекст в базу данных для заданного английского слова.*/
    private void addContentToDataBase(String contentType) {
        logger.info("Старт метода Word.addContextToDataBase");
        Connection connection = DatabaseConnection.getConnection();
        String content;

        if (!(contentType.equals("context") || contentType.equals("usage_examples")))
            throw new WordTypeException();

        try {
            content = Api.getResponse(getEnWord(), contentType);
        } catch (IOException e) {
            logger.error("Ошибка получения контекста из API " + e);
            throw new RuntimeException(e);
        }

        String sql = "INSERT INTO word_contexts (english_word, " + contentType + ") VALUES (?, ?) " +
                "ON CONFLICT (english_word) DO UPDATE SET " + contentType + " = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, getEnWord());
            preparedStatement.setString(2, content);
            preparedStatement.setString(3, content);

            preparedStatement.execute();
            logger.info("Контекст успешно добавлен в Базу данных");
        } catch (SQLException e) {
            logger.error("Ошибка добавления контекста в Базу данных");
            throw new RuntimeException(e);
        }
    }

    /* Метод добавляет новое слово в словарь пользователя.
    Если слово уже есть в словаре, метод возвращает сообщение об этом. Если слово не найдено в базе данных,
    оно добавляется в базу данных и затем добавляется в словарь пользователя.*/
    public static Set<Integer> add(@NotNull String wordForAdd, Long userId) throws TranslationException {
        nullCheck.checkForNull("add ", wordForAdd, userId);

        Set<Integer> wordIdList = new HashSet<>();
        checkNewWordInDB(wordForAdd, wordIdList);

        if (wordIdList.size() == 0) {
            addNewWordToDBFromTranslator(wordForAdd, wordIdList);
            for (Integer temp : wordIdList) {
                Word word = Word.getWord(temp);
                logger.info("Слово отправлено для получения контекста");
                Runnable runnable = () -> {
                    word.addContentToDataBase("context");
                    word.addContentToDataBase("usage_examples");
                };
                new Thread(runnable).start();
            }
        }

        checkWordInUserDictionary(wordIdList, userId);

        return wordIdList;
    }

    private void addTranscription() {
        logger.info("Старт метода Word.addTranscription");
        String transcription = null;
        try {
            transcription = Api.getResponse(getEnWord(), "transcription");
        } catch (IOException e) {
            logger.error("Word.addTranscription Ошибка получения транскрипции");
        }
        if (transcription == null) {
            logger.error("Word.addTranscription транскрипция вернулась null");
            return;
        }

        if (!transcription.matches("^\\[.*\\]$")) {
            logger.error("Word.addTranscription транкрипция не соответствует регулярному выражению");
            return;
        }

        Connection connection = DatabaseConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "UPDATE words SET transcription = ? WHERE word_id = ?")) {
            preparedStatement.setString(1, transcription);
            preparedStatement.setInt(2, getWordId());

            preparedStatement.executeUpdate();
            logger.info("Word.addTranscription Транскрипция успешно добавлена");
        } catch (SQLException e) {
            logger.error("Word.addTranscription не удалось добавить транскрипцию в Базу данных");
            throw new RuntimeException(e);
        }

    }

    /*Этот метод проверяет наличие слова в базе данных. Если слово найдено в базе данных,
    его id добавляется в множество wordId. Метод ничего не возвращает, только изменяет переданное ему множество.*/
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
            logger.info("Проверка нового слова в БД прошла успешно");
        } catch (SQLException e) {
            logger.error("Ошибка проверки нового слова в Базе данных " + e);
            throw new RuntimeException(e);
        }
    }

    /*Метод добавляет новое слово в базу данных, полученное из переводчика.
    В случае успеха, возвращает идентификатор добавленного слова.
    В случае ошибки, выбрасывает исключение TranslationException.*/
    public static ArrayList<String> addNewWordToDBFromTranslator(String word, Set<Integer> wordId) throws TranslationException {
        nullCheck.checkForNull("addNewWordToDBFromTranslator ", word, wordId);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("addNewWordToDBFromTranslator connection ", connection);
        var translatorResult = Word.translate(word);
        if (!word.equalsIgnoreCase(translatorResult.get(0)) && !word.equalsIgnoreCase(translatorResult.get(1))) {
            logger.error("Cлово " + word + " Вернулось из словаря неправильна. оба перевода не совпадают");
            throw new TranslationException();
        }
        translatorResult.set(0, translatorResult.get(0).substring(0, 1).toUpperCase() + translatorResult.get(0).substring(1).toLowerCase());
        translatorResult.set(1, translatorResult.get(1).substring(0, 1).toUpperCase() + translatorResult.get(1).substring(1).toLowerCase());
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO words (english_word, russian_word) VALUES (?, ?)");
            ps.setString(1, translatorResult.get(0));
            ps.setString(2, translatorResult.get(1));
            ps.executeUpdate();
            logger.info("Слово успешно добавлено в общий словарь");
            ps = connection.prepareStatement("SELECT lastval()");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                wordId.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            logger.error("Ошибка добавления в общий словарь " + e);
        }
        return translatorResult;
    }

    /*Метод проверяет, есть ли слова из набора в словаре пользователя.
    Если слово уже есть в словаре, оно удаляется из набора. */
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
            logger.info("Поиск слова в словаре пользователя прошла успешно.");
        } catch (SQLException e) {
            logger.error("Ошибка поиска слова в словаре пользователя  " + e);
            throw new RuntimeException(e);
        }
    }

    /*Метод добавляет новые слова в словарь пользователя.
    Он принимает набор идентификаторов слов и идентификатор пользователя, для которого нужно добавить слова.
    В цикле происходит добавление каждого слова в таблицу пользовательских слов.*/
    public void addNewWordsToUserDictionary(Long userId) {
        nullCheck.checkForNull("addNewWordsToUserDictionary ", userId);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("addNewWordsToUserDictionary connection ", connection);
        try {
            PreparedStatement ps = connection.prepareCall("insert into user_word_lists (user_id, word_id) VALUES (?, ?)");
            ps.setLong(1, userId);
            ps.setInt(2, getWordId());
            ps.executeUpdate();
            logger.info("Слово успешно добавлено в словарь пользователя.");
        } catch (SQLException e) {
            logger.error("Не удалось добавить слово в словарь пользователя " + e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Возвращает список слов определенного типа, связанных с пользователем.
     *
     * @param userId   идентификатор пользователя
     * @param listType тип списка слов ("learning" или "repetition")
     * @return список строк с информацией о словах или пустой список, если список пуст
     */
    public static ArrayList<String> fetchUserWords(Long userId, String listType) {
        nullCheck.checkForNull("getWordList ", userId, listType);
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getWordList Connection ", connection);
        var messagesList = new ArrayList<String>();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT w.word_id, uwl.timer_value " +
                        "FROM words w " +
                        "JOIN user_word_lists uwl ON w.word_id = uwl.word_id " +
                        "WHERE uwl.user_id = ? AND uwl.list_type = ?"
        )) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, listType);
            ResultSet resultSet = preparedStatement.executeQuery();

            switch (listType.toLowerCase()) {
                case "learning" -> messagesList = processLearningList(resultSet);
                case "repetition" -> messagesList = processRepetitionList(resultSet);
            }
        } catch (SQLException e) {
            logger.error("Получение листа из БД", e);
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
            stringBuilder.append(word).append("\n");
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
                    stringBuilder.append(word.toString()).append("\n");
                }
                messagesList.add(stringBuilder.toString().trim());
            }
        }

        return messagesList;
    }

    private static ArrayList<String> splitMessageText(String text) {
        nullCheck.checkForNull("splitMessageText ", text);
        String[] texts = text.split(" {2}- {2}");
        ArrayList<String> result = new ArrayList<>();
        for (String temp : texts) {
            result.add(temp.trim());
        }

        return result;
    }

    /*Метод для удаления слова из списка пользователя. Проверяет наличие необходимых параметров,
    устанавливает соединение с базой данных, выполняет SQL-запрос на удаление слова из списка и логирует результат.
    Если удаление не удалось, выбрасывает исключение.*/
    public Boolean deleteWordFromUserList(Long userId) {
        nullCheck.checkForNull("removeWord", userId);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("removeWord Connection", connection);
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "DELETE FROM user_word_lists where user_id = ? AND word_id = ?;")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setInt(2, getWordId());

            preparedStatement.execute();
            logger.info("Слово успешно удалено из словаря");
            return true;
        } catch (SQLException e) {
            logger.error("Не удалось удалить слово из словаря" + e);
            return false;
        }
    }

    /*Метод проверяет, содержит ли словарь пользователя заданное слово.
    Возвращает true, если слово найдено, иначе false. Используется подключение к БД.*/
    public Boolean checkWordInUserList() {
        logger.info("Начинается чек слова в словаре пользователя");

        try (PreparedStatement preparedStatement = DatabaseConnection.getConnection().prepareStatement(
                "SELECT * FROM user_word_lists WHERE word_id = ?")) {
            preparedStatement.setInt(1, getWordId());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next())
                return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    /*Метод возвращает случайное сочетание слов на английском и русском языках.*/
    public String toStringRandom() {
        boolean random = new Random().nextBoolean();
        String tempEnWord = getEnWord().substring(0, 1).toUpperCase() + getEnWord().substring(1);
        String tempRuWord = getRuWord().substring(0, 1).toUpperCase() + getRuWord().substring(1);

        return random ? tempEnWord + "  -  " + " <span class='tg-spoiler'> " + tempRuWord + "</span>" : tempRuWord + "  -  " + " <span class='tg-spoiler'> " + tempEnWord + "</span>";
    }

    public String toStringRandomWithTranscription() {
        boolean random = new Random().nextBoolean();
        String tempEnWord = getEnWord().substring(0, 1).toUpperCase() + getEnWord().substring(1) + "   " + getTranscription();
        String tempRuWord = getRuWord().substring(0, 1).toUpperCase() + getRuWord().substring(1);

        return random ? tempEnWord + "  -  " + " <span class='tg-spoiler'> " + tempRuWord + "</span>" : tempRuWord + "  -  " + " <span class='tg-spoiler'> " + tempEnWord + "</span>";
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