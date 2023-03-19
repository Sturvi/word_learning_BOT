package telegramBot;

import dataBase.DatabaseConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import telegramBot.user.User;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*Данный класс подключается к телеграмм боту, принимает обновления,
распледеляет задачи и отправляет сообщения пользователям*/
public class TelegramApiConnect extends TelegramLongPollingBot {

    @Override
    public void onUpdateReceived(Update update) {

        DatabaseConnection.checkUser(update.getMessage());

        Long chatId = update.getMessage() == null ? update.getCallbackQuery().getMessage().getChatId() : update.getMessage().getChatId();

        //Если команда пришла от админа, ее обработка уходит в класс Админ
        if (chatId.equals(Main.admin.getChatID())) {
            Main.admin.inputCommand(update);
            return;
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        } else {
            handleTextMessage(update.getMessage());
        }
    }

    /*Оброботка нажаний на клавиши команд*/
    private void handleCallback(@NotNull CallbackQuery callbackQuery) {
        Message message = callbackQuery.getMessage();
        Long userId = message.getChatId();
        String data = callbackQuery.getData();
        String text = callbackQuery.getMessage().getText();


        switch (data) {
            case ("delete") -> {
                User.removeWord(userId, text);
                editKeyboardAfterDeleteMessage(callbackQuery);
            }
            case ("next") -> {
                getRandomWordAndSendToUser(message);
            }
            case ("learned") -> {
                 User.changeWordListType(userId, "repetition", text);
                editKeyboardAfterLeanedOrForgot(callbackQuery);
            }
            case ("forgot") -> {
                User.changeWordListType(userId, "learning", text);
                editKeyboardAfterLeanedOrForgot(callbackQuery);
            }
        }
    }

    /*Оброботка текстовых команд или в случае, если пользователь присылает слова на добавление в словарь*/
    private void handleTextMessage(@NotNull Message message) {
        String InputMessageText = message.getText();
        Long userId = message.getChatId();

        switch (InputMessageText) {
            case ("/start") -> {
                sendMessage(message, "Добро пожаловать в наш бот по изучению английских слов.");
            }
            case ("\uD83D\uDDD2 Добавить слова") -> {
                User.setMenu(userId, "inAddMenu");
                sendMessage(message, """
                        Можете отправлять слова, которые хотите добавить в свою коллекцию.\s

                        Если нужно добавить несколько слов, можете отправлять их по очереди.

                        Можете отправлять также словосочетания

                        Учтите, что слова переводятся автоматически, с помощью сервисов онлайн перевода и никак не проходят дополнительные проверки орфографии. Поэтому даже при небольших ошибках, перевод также будет ошибочный.""");
            }
            case ("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова") -> {
                User.setMenu(userId, "learning");
                getRandomWordAndSendToUser(message);
            }
            case ("\uD83D\uDD01 Повторять слова") -> {
                User.setMenu(userId, "repetition");
                getRandomWordAndSendToUser(message);
            }
            case ("\uD83D\uDCD3 Список изучаемых слов") -> {
                User.setMenu(userId, "AllFalse");
                String messageText = User.getWordList(userId, "learning");
                if (messageText == null) messageText = "В вашем словаре нет слов на изучении";
                sendMessage(message, messageText);
            }
            case ("\uD83D\uDCD3 Список слов на повторении") -> {
                User.setMenu(userId, "AllFalse");
                String messageText = User.getWordList(userId, "repetition");
                if (messageText == null) messageText = "В вашем словаре нет слов на повторении";
                sendMessage(message, messageText);
            }
            case ("\uD83D\uDCC8 Статистика") -> {
                sendMessage(message, User.getStatistic(userId));
            }
            default -> {
                String menu = User.getUserMenu(userId);
                if (menu.equals("inAddMenu")) {
                    sendMessage(message, User.add(InputMessageText, userId), true);
                }
            }
        }
    }

    private void editKeyboardAfterLeanedOrForgot(CallbackQuery callbackQuery) {
        Message message = callbackQuery.getMessage();
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(message.getChatId());
        editMessageReplyMarkup.setMessageId(message.getMessageId());

        InlineKeyboardMarkup keyboard = getKeyboard(message.getChatId());

        String[] texts = message.getText().split(" - ");
        texts[0] = texts[0].trim();
        texts[1] = texts[1].trim();

        Long userId = message.getChatId();
        Connection connection = DatabaseConnection.getConnection();
        String list_type = null;
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT list_type FROM user_word_list WHERE user_id = ? AND word_id = " +
                        "(SELECT word_id FROM words " +
                        " WHERE (russian_word = ? AND english_word = ?) " +
                        " OR (russian_word = ? AND english_word = ?))")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, texts[0]);
            preparedStatement.setString(3, texts[1]);
            preparedStatement.setString(4, texts[1]);
            preparedStatement.setString(5, texts[0]);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                list_type = resultSet.getString("list_type");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        if (list_type.equalsIgnoreCase("repetition")) {
            InlineKeyboardButton forgot = new InlineKeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Снова изучать это слово");
            forgot.setCallbackData("forgot");
            keyboard.getKeyboard().get(0).set(1, forgot);
        } else if (list_type.equalsIgnoreCase("learning")) {
            InlineKeyboardButton learned = new InlineKeyboardButton("\uD83E\uDDE0 Уже знаю это слово");
            learned.setCallbackData("learned");
            keyboard.getKeyboard().get(0).set(1, learned);
        }

        editMessageReplyMarkup.setReplyMarkup(keyboard);

        try {
            execute(editMessageReplyMarkup);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void editKeyboardAfterDeleteMessage(CallbackQuery callbackQuery) {
        Message message = callbackQuery.getMessage();
        String[] text = message.getText().split(" - ");

        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setMessageId(message.getMessageId());
        editMessageText.setChatId(message.getChatId());
        editMessageText.setText("Слово " + text[0] + " удалено из вашего словаря");
        editMessageText.setReplyMarkup(getKeyboard(message.getChatId(), false));

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void getRandomWordAndSendToUser(@NotNull Message message) {
        Long userId = message.getChatId();
        String menu = getUserMenu(userId);

        if (menu == null) {
            sendMessage(message, "Что-то пошло не так. Мы сообщили об этом Администратору. Скоро все исправим!");
            return;
        } else if (!(menu.equals("learning") || menu.equals("repetition"))) {
            sendMessage(message, "Вы не выбрали меню. Пожалуйста выбери меню изучения или повторения слов");
            return;
        }

        Word word = getWord(userId);

        if (menu.equals("learning") && word == null) {
            sendMessage(message, "У вас нет слов для изучения в данный момент. Пожалуйста, " +
                    "добавьте новые слова, или воспользуйтесь нашим банком слов.");
            return;
        }

        if (menu.equals("repetition") && word == null) {
            sendMessage(message, "У вас нет слов на повторении в данный момент. Пожалуйста, " +
                    "воспользуйтесь меню \"\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова\"");
            return;
        }

        sendWordWithVoice(word, message);
    }

    private @Nullable String getUserMenu(Long userId) {
        Connection connection = DatabaseConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT menu_name FROM user_menu WHERE user_id = ?")) {
            preparedStatement.setLong(1, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next())
                return resultSet.getString("menu_name");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private @Nullable Word getWord(Long userId) {
        Connection connection = DatabaseConnection.getConnection();

        String russianWord = null;
        String englishWord = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT russian_word, english_word FROM words " +
                        "WHERE word_id = " +
                        "(SELECT word_id FROM user_word_list " +
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
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        if (russianWord != null && englishWord != null)
            return new Word(englishWord, russianWord);
        else return null;
    }

    /*Данный метод отправляет пользователю слово c произношением. В случае невозможность получить аудио файл с произношением
    отправляет просто слово*/
    private void sendWordWithVoice(Word word, Message message) {
        String textForMessage;
        int random = new Random().nextInt(2);

        if (random == 0) {
            textForMessage = word.getEnWord() + " -  <span class='tg-spoiler'>   " + word.getRuWord() + "   </span>";
        } else {
            textForMessage = word.getRuWord() + " -  <span class='tg-spoiler'>   " + word.getEnWord() + "   </span>";
        }

        File voice;
        try {
            voice = word.getVoice();
        } catch (Exception e) {
            sendMessage(message, textForMessage, getKeyboard(message.getChatId()));
            return;
        }

        InputFile inputFile = new InputFile(voice);
        SendAudio audio = new SendAudio();
        audio.setTitle("Произношение слова");
        audio.setChatId(message.getChatId().toString());
        audio.setAudio(inputFile);

        try {
            execute(audio);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

        sendMessage(message, textForMessage, getKeyboard(message.getChatId()));
    }

    /*Отправка обычных тектовых сообщений.*/
    public void sendMessage(Message message, String text) {
        sendMessage(message, text, false);
    }

    /*Отправка обычных тектовых сообщений с привязкой клавиатуры.*/
    public void sendMessage(Message message, String text, InlineKeyboardMarkup inlineKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        sendMsg(sendMessage);
    }

    public void sendMessage(Message message, String text, boolean setReplyToMessageId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setText(text);
        sendMessage.setChatId(message.getChatId());

        if (setReplyToMessageId) {
            sendMessage.setReplyToMessageId(message.getMessageId());
        }

        setButtons(sendMessage);
        sendMsg(sendMessage);
    }

    public void sendMsg(SendMessage sendMessage) {
        sendMessage.enableMarkdown(true);
        sendMessage.enableHtml(true);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /*Нижние клавиши*/
    public void setButtons(SendMessage sendMessage) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboardRowList = new ArrayList<>();
        KeyboardRow keyboardFirstRow = new KeyboardRow();
        KeyboardRow keyboardSecondRow = new KeyboardRow();
        KeyboardRow keyboardThirdRow = new KeyboardRow();

        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDDD2 Добавить слова"));
        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDCC8 Статистика"));
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDCD3 Список слов на повторении"));
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDCD3 Список изучаемых слов"));
        keyboardThirdRow.add(new KeyboardButton("\uD83D\uDD01 Повторять слова"));
        keyboardThirdRow.add(new KeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова"));

        keyboardRowList.add(keyboardFirstRow);
        keyboardRowList.add(keyboardSecondRow);
        keyboardRowList.add(keyboardThirdRow);

        replyKeyboardMarkup.setKeyboard(keyboardRowList);
    }

    private InlineKeyboardMarkup getKeyboard(Long chatId) {
        return getKeyboard(chatId, true);
    }

    private InlineKeyboardMarkup getKeyboard(Long chatId, boolean deleteFirstLine) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());
        keyboard.add(new ArrayList<>());

        if (deleteFirstLine) {
            InlineKeyboardButton delete = new InlineKeyboardButton("❌ Удалить это слово");
            delete.setCallbackData("delete");
            keyboard.get(0).add(delete);

            if (telegramBot.Main.userMap.get(chatId).isInLeaningMenu()) {
                InlineKeyboardButton learned = new InlineKeyboardButton("\uD83E\uDDE0 Уже знаю это слово");
                learned.setCallbackData("learned");
                keyboard.get(0).add(learned);
            } else if (telegramBot.Main.userMap.get(chatId).isInRepeatMenu()) {
                InlineKeyboardButton forgot = new InlineKeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Снова изучать это слово");
                forgot.setCallbackData("forgot");
                keyboard.get(0).add(forgot);
            }
        }

        InlineKeyboardButton next = new InlineKeyboardButton("➡ Следующее слово");
        next.setCallbackData("next");
        keyboard.get(1).add(next);

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        return keyboardMarkup;
    }

    @Override
    public String getBotUsername() {
        //return "SturviTestBot"; // test bot name
        return "@WordLeaningBot";
    }

    @Override
    public String getBotToken() {
        //return "5857743410:AAHyinYvlTc-grG76012Nqj6Of5SGNgmMvE"; // test token
        return "5915434126:AAHto2nUM8S1a9cb2Fgxz8F3P45BV4QGp7U";
    }
}