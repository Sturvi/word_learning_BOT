package telegramBot;

import Exceptions.ChatGptApiException;
import com.google.gson.Gson;
import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;

import java.net.URI;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class api {

    private static final Logger logger = Logger.getLogger(api.class);
    private static final NullCheck nullCheck = () -> logger;

    public static String getResponse(String text) throws IOException {
        nullCheck.checkForNull("getResponse ", text);

        String input = """
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
        logger.info("Пронт на слово " + text + " составлен");

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getApiKey("OpenAI"))
                .POST(HttpRequest.BodyPublishers.ofString(input))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> respone = null;
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

        // Получаем контекст из объекта ChatCompletion.
        return chatCompletion.getContext();
    }

    /*Метод getApiKey() используется для получения ключа API из базы данных. Если ключ существует,
    метод возвращает его значение. В противном случае генерируется исключение RuntimeException.
    .*/
    public static String getApiKey(String apiName) {
        try (PreparedStatement preparedStatement = DatabaseConnection.getConnection().prepareStatement(
                "SELECT api_key From api_keys WHERE name = ?;")) {
            preparedStatement.setString(1, apiName);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()){
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
}

class ChatCompletion {
    private String id;
    private String object;
    private long created;
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

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
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

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
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
        private int prompt_tokens;
        private int completion_tokens;
        private int total_tokens;

        public int getPromptTokens() {
            return prompt_tokens;
        }

        public void setPromptTokens(int prompt_tokens) {
            this.prompt_tokens = prompt_tokens;
        }

        public int getCompletionTokens() {
            return completion_tokens;
        }

        public void setCompletionTokens(int completion_tokens) {
            this.completion_tokens = completion_tokens;
        }

        public int getTotalTokens() {
            return total_tokens;
        }

        public void setTotalTokens(int total_tokens) {
            this.total_tokens = total_tokens;
        }
    }

    public static class Choice {
        private Message message;
        private String finish_reason;
        private int index;

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        public String getFinishReason() {
            return finish_reason;
        }

        public void setFinishReason(String finish_reason) {
            this.finish_reason = finish_reason;
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
