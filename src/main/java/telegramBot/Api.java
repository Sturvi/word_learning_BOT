package telegramBot;

import Exceptions.ChatGptApiException;
import com.google.gson.Gson;
import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.telegram.telegrambots.meta.api.objects.Message;

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

    private static final Logger logger = Logger.getLogger(Api.class);
    private static final NullCheck nullCheck = () -> logger;

    public static String getResponse(String text, String contentType) throws IOException {
        logger.info("Старт метода Api.getResponse");
        nullCheck.checkForNull("getResponse ", text);
        String promt;
        switch (contentType) {
            case ("context") -> promt = """
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
            case ("usage_examples") -> promt = """
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
            case ("transcription") -> promt = """
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
                logger.error("Неправильный contentType");
                throw new ChatGptApiException();
            }
        }
        logger.info("Промт на слово " + text + " составлен");

        return openAiHttpRequest(promt);
    }

    private static String openAiHttpRequest(String promt) throws IOException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getApiKey("OpenAI2"))
                .POST(HttpRequest.BodyPublishers.ofString(promt))
                .timeout(Duration.ofSeconds(45))
                .build();


        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> respone;
        try {
            respone = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Запрос в Chat gpt API отправлен.");
        } catch (InterruptedException e) {
            logger.error("Ошибка отправки запроса в  Chat gpt API");
            throw new RuntimeException(e);
        }

        String jsonString = respone.body();

        Gson gson = new Gson();

        // Парсим JSON объект в экземпляр класса ChatCompletion.
        ChatCompletion chatCompletion = gson.fromJson(jsonString, ChatCompletion.class);

        if (chatCompletion.getContext() == null) {
            logger.error("Ошибка получения контента. вернулся null");
            throw new ChatGptApiException();
        }

        return chatCompletion.getContext();
    }

    public static Boolean hasModerationPassed(String wordForAdd) throws IOException {
        logger.info("Старт метода Api.hasModerationPassed");
        nullCheck.checkForNull("hasModerationPassed ", wordForAdd);
        String text = "Привет! Я хотел бы добавить слово в свой словарь. Можете ли вы проверить, является ли слово " + wordForAdd +
                " корректным на английском или русском языке, и можно ли его добавить в мою базу данных, " +
                "которая содержит как английские, так и русские слова? Ответ должен содержать только true или false. " +
                "Максимальная длина ответа 5 символов.  Кроме того, я хотел бы убедиться, что любые опечатки будут " +
                "рассматриваться как ошибки, как на английском, так и на русском языке. Спасибо!";
        String promt = """
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


        String result[] = openAiHttpRequest(promt).trim().split("\\P{L}+");

        if (result[0].equalsIgnoreCase("true")) {
            return true;
        } else if (result[0].equalsIgnoreCase("false")) {
            return false;
        }

        throw new ChatGptApiException();
    }

    public static void moderation (String wordForAdd, Word word, Message message){
        Runnable runnable = () -> {
            logger.info("Слово отправлено на проверку в Chat GPT");
            try {
                logger.info("Слово отправлено для получения контекста");
                if (!Api.hasModerationPassed(wordForAdd)){
                    String messageText = "К сожалению слово \"" + wordForAdd +
                            "\", которую вы пытались добавить в свой словарь не прошло модерацию и удалено!\n" +
                            "Одна из возможных причин, ошибка в наборе слова. " +
                            "Пожалуйста проверьте правильно ли вы ввели слова и попробуйте заново.";
                    new TelegramApiConnect().sendMessage(message, messageText);
                    word.deleteWordFromDataBase();
                } else {
                    logger.info("Слово удачно прошло модерацию");
                    word.addContentToDataBase("context");
                    word.addContentToDataBase("usage_examples");
                }
            } catch (IOException e) {
                logger.error("Ошибка во время обращения к OpenAI " + e);
            }
        };
        new Thread(runnable).start();
    }

    /*Метод getApiKey() используется для получения ключа API из базы данных. Если ключ существует,
    метод возвращает его значение. В противном случае генерируется исключение RuntimeException.
    .*/
    public static String getApiKey(String apiName) {
        try (PreparedStatement preparedStatement = DatabaseConnection.getConnection().prepareStatement(
                "SELECT api_key From api_keys WHERE name = ?;")) {
            preparedStatement.setString(1, apiName);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                logger.info("Ключ API получен");
                return resultSet.getString("api_key");
            } else {
                logger.error("Resul SET вернулся null");
                throw new RuntimeException();
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения ключа из БД " + e);
            throw new RuntimeException(e);
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

