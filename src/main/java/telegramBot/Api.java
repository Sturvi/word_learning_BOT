package telegramBot;

import Exceptions.ChatGptApiException;
import com.google.gson.Gson;
import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

public class Api {

    private static final Logger LOGGER = Logger.getLogger(Api.class);
    private static final NullCheck nullCheck = () -> LOGGER;

    /**
     * Получает ответ от Chat GPT API на основе текста и типа контента.
     *
     * @param text         Текст, на основе которого получается ответ.
     * @param contentType  Тип контента для получения ответа (например, "context" или "usage_examples").
     * @return Строка, содержащая ответ от API.
     * @throws IOException В случае ошибки сети или обработки запроса.
     */
    public static String getResponse(String text, @NotNull String contentType) throws IOException {
        LOGGER.info("Старт метода Api.getResponse");
        nullCheck.checkForNull("getResponse ", text);

        // Формирование промпта на основе contentType
        String prompt;
        switch (contentType) {
            case "context" -> prompt = """
                {
                  "model": "gpt-3.5-turbo",
                  "messages": [
                    {
                      "role": "system",
                      "content": "Пришли контекст слова %s на русском максимум в 150 символов"
                    }
                  ]
                }
                """.formatted(text);
            case "usage_examples" -> prompt = """
                {
                  "model": "gpt-3.5-turbo",
                  "messages": [
                    {
                      "role": "system",
                      "content": "Пришли 5 примеров использования слова %s. больше ничего не пиши. Ответ должен полностью соответствовать шаблону. Между переводами символ \\n. перед новой фразой символ \\n\\n. Все должно быть написано в одну строку. Например: I need to purchase additional supplies \\nМне нужно купить дополнительные принадлежности \\n\\n The hotel charges extra for additional guests $$nОтель берет дополнительную плату за дополнительных гостей \\n\\ne will need additional time to finish the project \\nНам понадобится дополнительное время, чтобы завершить проект."
                    }
                  ]
                }
                """.formatted(text);
            case "transcription" -> prompt = """
                {
                  "model": "gpt-3.5-turbo",
                  "messages": [
                    {
                      "role": "system",
                      "content": "Пришли транскрипцию к слову %s. Пусть начинается с символа [ и заканчивается символом ] . больше ничего не пиши"
                    }
                  ]
                }
                """.formatted(text);
            default -> {
                LOGGER.error("Неправильный contentType");
                throw new ChatGptApiException();
            }
        }

        LOGGER.info("Промпт на слово " + text + " составлен");

        // Отправка запроса к OpenAI Chat GPT API и получение ответа
        return openAiHttpRequest(prompt);
    }

    /**
     * Отправляет запрос к OpenAI Chat GPT API с указанным текстом.
     *
     * @param prompt Текст для отправки в запросе.
     * @return Строка, содержащая ответ от API.
     * @throws IOException В случае ошибки сети или обработки запроса.
     */
    private static String openAiHttpRequest(String prompt) throws IOException {
        // Создание запроса к OpenAI Chat GPT API
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getApiKey("OpenAI2"))
                .POST(HttpRequest.BodyPublishers.ofString(prompt))
                .timeout(Duration.ofSeconds(45))
                .build();

        // Создание HTTP клиента
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response;

        // Отправка запроса и получение ответа
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            LOGGER.info("Запрос в Chat GPT API отправлен.");
        } catch (InterruptedException e) {
            LOGGER.error("Ошибка отправки запроса в Chat GPT API");
            throw new RuntimeException(e);
        }

        // Получение тела ответа в виде строки
        String jsonString = response.body();

        // Инициализация Gson для преобразования JSON в объект
        Gson gson = new Gson();

        // Парсинг JSON объекта в экземпляр класса ChatCompletion
        ChatCompletion chatCompletion = gson.fromJson(jsonString, ChatCompletion.class);

        // Проверка наличия контента в ответе и генерация исключения, если контент отсутствует
        if (chatCompletion.getContext() == null) {
            LOGGER.error("Ошибка получения контента. вернулся null");
            throw new ChatGptApiException();
        }

        // Возвращение контента из ответа API
        return chatCompletion.getContext();
    }

    /**
     * Проверяет, является ли слово корректным на английском или русском языке, и можно ли его добавить в базу данных.
     *
     * @param wordForAdd Слово, которое нужно проверить.
     * @return true, если слово корректно на английском или русском языке, иначе false.
     * @throws IOException В случае ошибки ввода-вывода.
     */
    public static @NotNull Boolean hasModerationPassed(String wordForAdd) throws IOException {
        LOGGER.info("Старт метода Api.hasModerationPassed");
        nullCheck.checkForNull("hasModerationPassed ", wordForAdd);

        // Проверка корректности слова на английском языке
        boolean engControl = isWordValid(wordForAdd, "английском", "английские");
        // Проверка корректности слова на русском языке
        boolean ruControl = isWordValid(wordForAdd, "русском", "русские");

        return engControl || ruControl;
    }

    /**
     * Проверяет, является ли слово корректным на указанном языке и можно ли его добавить в базу данных.
     *
     * @param word          Слово, которое нужно проверить.
     * @param language      Язык, на котором проверяется корректность слова. Будет вставляться в промт в предложном падеже.
     * @param languageWords Название того же языка, что и в параметре language, только в множественном числе. Будет вставляться в промт.
     * @return true, если слово корректно на указанном языке, иначе false.
     * @throws IOException В случае ошибки ввода-вывода.
     */
    private static boolean isWordValid(String word, String language, String languageWords) throws IOException {
        // Формирование текста запроса с использованием параметров
        String text = "Привет! Я хотел бы добавить слово в свой словарь. Можете ли вы проверить, является ли слово " + word +
                " корректным на " + language + " языке, и можно ли его добавить в мою базу данных, " +
                "которая содержит " + languageWords + " слова? Ответ должен содержать только true или false. " +
                "Максимальная длина ответа 5 символов.  Кроме того, я хотел бы убедиться, что любые опечатки будут " +
                "рассматриваться как ошибки. Спасибо!";

        // Формирование запроса к API на основе текста
        String prompt = """
                {
                  "model": "gpt-3.5-turbo",
                  "messages": [
                    {
                      "role": "system",
                      "content": "%s"
                    }
                  ]
                }
                """.formatted(text);

        // Выполнение запроса и разбиение полученного ответа на массив строк
        String[] result = openAiHttpRequest(prompt).trim().split("\\P{L}+");

        // Возвращение результата проверки корректности слова на указанном языке
        return result[0].equalsIgnoreCase("true");
    }

    /**
     * Выполняет модерацию слова с помощью Chat GPT и обрабатывает результат.
     * Запускает отдельный поток для асинхронной модерации слова и добавления контекста и примеров использования.
     *
     * @param wordForAdd Слово для проверки и добавления.
     * @param word       Объект Word, представляющий слово и его связанные данные.
     * @param user       Объект BotUser, содержащий информацию о пользователе и его сообщении.
     */
    public static void moderation(String wordForAdd, Word word, BotUser user) {
        LOGGER.info("Старт метода Модерации");
        // Создание отдельного потока для асинхронной модерации слова
        Runnable moderationTask = () -> {
            LOGGER.info("Новый поток модерации слова " + wordForAdd + ". Слово отправлено на проверку в Chat GPT");
            try {
                LOGGER.info("Слово отправлено для получения контекста");

                // Проверка слова на прохождение модерации
                if (!Api.hasModerationPassed(wordForAdd)) {
                    // Если слово не прошло модерацию, отправляем сообщение пользователю и удаляем слово из базы данных
                    String messageText = "К сожалению, слово \"" + wordForAdd +
                            "\", которое вы пытались добавить в свой словарь, не прошло модерацию и было удалено!\n" +
                            "Одна из возможных причин — ошибка в наборе слова. " +
                            "Пожалуйста, проверьте правильность написания слова и попробуйте заново.";
                    new TelegramApiConnect().sendMessage(user, messageText);
                    word.deleteWordFromDataBase();
                } else {
                    // Если слово прошло модерацию, добавляем контекст и примеры использования в базу данных
                    LOGGER.info("Слово удачно прошло модерацию");
                    word.addContentToDataBase("context");
                    word.addContentToDataBase("usage_examples");
                }
            } catch (IOException e) {
                LOGGER.error("Ошибка во время обращения к OpenAI " + e);
            }
        };

        // Запуск потока для выполнения модерации
        new Thread(moderationTask).start();
    }

    /**
     * Получает ключ API для указанного имени API из базы данных.
     * Если ключ существует, метод возвращает его значение, иначе генерируется исключение RuntimeException.
     *
     * @param apiName Имя API, для которого требуется получить ключ.
     * @return Строка, содержащая ключ API.
     * @throws RuntimeException В случае, если ключ API не найден в базе данных.
     */
    public static String getApiKey(String apiName) {
        // Запрос на получение ключа API из таблицы api_keys по имени API
        String query = "SELECT api_key FROM api_keys WHERE name = ?;";

        try (PreparedStatement preparedStatement = DatabaseConnection.getConnection().prepareStatement(query)) {
            // Установка значения параметра в запросе
            preparedStatement.setString(1, apiName);

            // Выполнение запроса и получение результата
            ResultSet resultSet = preparedStatement.executeQuery();

            // Если результат содержит запись, возвращаем значение ключа API
            if (resultSet.next()) {
                LOGGER.info("Ключ API получен");
                return resultSet.getString("api_key");
            } else {
                LOGGER.error("Result SET вернулся null");
                throw new RuntimeException("API key not found for API name: " + apiName);
            }
        } catch (SQLException e) {
            LOGGER.error("Ошибка получения ключа из БД " + e);
            throw new RuntimeException("Error retrieving API key from database: ", e);
        }
    }

    static class ChatCompletion {
        private String id;
        private String object;
        private String model;
        private Usage usage;
        private List<Choice> choices;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Usage getUsage() {
            return usage;
        }

        public void setUsage(Usage usage) {
            this.usage = usage;
        }

        public String getContext() {
            if (choices != null && !choices.isEmpty()) {
                Message message = choices.get(0).getMessage();
                if (message != null) {
                    return message.getContent();
                }
            }
            return null;
        }

        public static class Usage {


        }

        public static class Choice {
            private Message message;

            private int index;

            public Message getMessage() {
                return message;
            }

            public void setMessage(Message message) {
                this.message = message;
            }

            public int getIndex() {
                return index;
            }

            public void setIndex(int index) {
                this.index = index;
            }
        }

        public static class Message {
            private String role;
            private String content;

            public String getRole() {
                return role;
            }

            public void setRole(String role) {
                this.role = role;
            }

            public String getContent() {
                return content;
            }

            public void setContent(String content) {
                this.content = content;
            }
        }
    }
}

