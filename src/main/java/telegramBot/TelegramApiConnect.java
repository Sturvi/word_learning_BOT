package telegramBot;

import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import telegramBot.user.BotsUser;

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

    private static final Logger logger = Logger.getLogger(TelegramApiConnect.class);

    @Override
    public void onUpdateReceived(Update update) {
        try {
            logger.info("Пришел новый запрос от пользователя");
            Message message;

            User user;
            if (update.getMessage() == null) {
                message = update.getCallbackQuery().getMessage();
                user = update.getCallbackQuery().getFrom();
            } else {
                message = update.getMessage();
                user = update.getMessage().getFrom();
            }
            Long chatId = message.getChatId();

            DatabaseConnection.checkUser(user);

            //Если команда пришла от админа, ее обработка уходит в класс Админ
            if (chatId.equals(Main.admin.getChatID())) {
                logger.info("Запрос пришел от админа");
                Main.admin.inputCommand(update);
                return;
            }

            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else {
                handleTextMessage(update.getMessage());
            }
        } catch (Exception e) {
            logger.error("ГДЕТО ОШИБКА! " + e + " " + update);
        }
    }

    /*Оброботка нажаний на клавиши команд*/
    private void handleCallback(@NotNull CallbackQuery callbackQuery) {
        logger.info("Начало обработки запроса в нажатие клавиши");
        Message message = callbackQuery.getMessage();
        Long userId = message.getChatId();
        String data = callbackQuery.getData();
        String text = callbackQuery.getMessage().getText();


        switch (data) {
            case ("delete") -> {
                logger.info("Запрос на удаления слова");
                BotsUser.removeWord(userId, text);
                editKeyboardAfterDeleteMessage(callbackQuery);
            }
            case ("next") -> {
                logger.info("Запрос на следующее слово");
                getRandomWordAndSendToUser(message);
            }
            case ("learned") -> {
                logger.info("Запрос на перевод слова на выученное");
                BotsUser.changeWordListType(userId, "repetition", text);
                editKeyboardAfterLeanedOrForgot(callbackQuery);
            }
            case ("forgot") -> {
                logger.info("Запрос на перевод слова на снова изучаемое");
                BotsUser.changeWordListType(userId, "learning", text);
                editKeyboardAfterLeanedOrForgot(callbackQuery);
            }
        }
    }

    /*Оброботка текстовых команд или в случае, если пользователь присылает слова на добавление в словарь*/
    private void handleTextMessage(@NotNull Message message) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("handleTextMessage ", message);

        String InputMessageText = message.getText();
        Long userId = message.getChatId();

        switch (InputMessageText) {
            case ("/start") -> {
                sendMessage(message, "Добро пожаловать в наш бот по изучению английских слов.");
            }
            case ("\uD83D\uDDD2 Добавить слова") -> {
                BotsUser.setMenu(userId, "inAddMenu");
                sendMessage(message, """
                        Можете отправлять слова, которые хотите добавить в свою коллекцию.\s

                        Если нужно добавить несколько слов, можете отправлять их по очереди.

                        Можете отправлять также словосочетания

                        Учтите, что слова переводятся автоматически, с помощью сервисов онлайн перевода и никак не проходят дополнительные проверки орфографии. Поэтому даже при небольших ошибках, перевод также будет ошибочный.""");
            }
            case ("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова") -> {
                BotsUser.setMenu(userId, "learning");
                getRandomWordAndSendToUser(message);
            }
            case ("\uD83D\uDD01 Повторять слова") -> {
                BotsUser.setMenu(userId, "repetition");
                getRandomWordAndSendToUser(message);
            }
            case ("\uD83D\uDCD3 Список изучаемых слов") -> {
                BotsUser.setMenu(userId, "AllFalse");
                String messageText = BotsUser.getWordList(userId, "learning");
                if (messageText == null) messageText = "В вашем словаре нет слов на изучении";
                sendMessage(message, messageText);
            }
            case ("\uD83D\uDCD3 Список слов на повторении") -> {
                BotsUser.setMenu(userId, "AllFalse");
                String messageText = BotsUser.getWordList(userId, "repetition");
                if (messageText == null) messageText = "В вашем словаре нет слов на повторении";
                sendMessage(message, messageText);
            }
            case ("\uD83D\uDCC8 Статистика") -> {
                BotsUser.setMenu(userId, "AllFalse");
                sendMessage(message, BotsUser.getStatistic(userId));
            }
            default -> {
                String menu = BotsUser.getUserMenu(userId);
                if (menu.equals("inAddMenu")) {
                    sendMessage(message, BotsUser.add(InputMessageText, userId), true);
                }
            }
        }
    }

    private void editKeyboardAfterLeanedOrForgot(CallbackQuery callbackQuery) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("editKeyboardAfterLeanedOrForgot", callbackQuery);

        logger.info("Начало редактирования клавиатуры под сообщениями");
        Message message = callbackQuery.getMessage();
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(message.getChatId());
        editMessageReplyMarkup.setMessageId(message.getMessageId());

        InlineKeyboardMarkup keyboard = getKeyboard(message.getChatId());

        String[] texts = message.getText().split("  -  ");
        nullCheck.checkForNull("editKeyboardAfterLeanedOrForgot", texts[0], texts[1]);
        texts[0] = texts[0].trim();
        texts[1] = texts[1].trim();


        Long userId = message.getChatId();
        Connection connection = DatabaseConnection.getConnection();
        String list_type = null;
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT list_type FROM user_word_lists WHERE user_id = ? AND word_id = " +
                        "(SELECT word_id FROM words " +
                        " WHERE (russian_word = ? AND english_word = ?) " +
                        " OR (russian_word = ? AND english_word = ?))")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, texts[0]);
            preparedStatement.setString(3, texts[1]);
            preparedStatement.setString(4, texts[1]);
            preparedStatement.setString(5, texts[0]);
            ResultSet resultSet = preparedStatement.executeQuery();
            nullCheck.checkForNull("editKeyboardAfterLeanedOrForgot", resultSet);

            while (resultSet.next()) {
                list_type = resultSet.getString("list_type");
                logger.info("list_type Получен из БД");
            }
        } catch (SQLException e) {
            logger.error("editKeyboardAfterLeanedOrForgot Ошибка получения list_type из БД");
            throw new RuntimeException(e);
        }

        if (list_type.equalsIgnoreCase("repetition")) {
            InlineKeyboardButton forgot = new InlineKeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Снова изучать это слово");
            forgot.setCallbackData("forgot");
            keyboard.getKeyboard().get(0).set(1, forgot);
            logger.info("Изменения в клавиатуре произведены");
        } else if (list_type.equalsIgnoreCase("learning")) {
            InlineKeyboardButton learned = new InlineKeyboardButton("\uD83E\uDDE0 Уже знаю это слово");
            learned.setCallbackData("learned");
            keyboard.getKeyboard().get(0).set(1, learned);
            logger.info("Изменения в клавиатуре произведены");
        }

        editMessageReplyMarkup.setReplyMarkup(keyboard);

        try {
            execute(editMessageReplyMarkup);
            logger.info("Изменения клавиатуры отправлены");
        } catch (TelegramApiException e) {
            logger.error("editKeyboardAfterLeanedOrForgot Ошибка отправки изменений клавиатуры");
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
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getRandomWordAndSendToUser", message);
        Long userId = message.getChatId();
        String menu = BotsUser.getUserMenu(userId);

        if (menu == null) {
            logger.error("getRandomWordAndSendToUser Меню из БД вернулось null");
            sendMessage(message, "Что-то пошло не так. Мы сообщили об этом Администратору. Скоро все исправим!");
            return;
        } else if (!(menu.equals("learning") || menu.equals("repetition"))) {
            sendMessage(message, "Вы не выбрали меню. Пожалуйста выбери меню изучения или повторения слов");
            return;
        }

        Word word = Word.getWord(userId);

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


    /*Данный метод отправляет пользователю слово c произношением. В случае невозможность получить аудио файл с произношением
    отправляет просто слово*/
    private void sendWordWithVoice(Word word, Message message) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("sendWordWithVoice ", word, message);
        String textForMessage;
        int random = new Random().nextInt(2);

        if (random == 0) {
            textForMessage = word.getEnWord() + "  -  <span class='tg-spoiler'>   " + word.getRuWord() + "   </span>";
        } else {
            textForMessage = word.getRuWord() + "  -  <span class='tg-spoiler'>   " + word.getEnWord() + "   </span>";
        }

        File voice;
        try {
            voice = word.getVoice();
            logger.info("Произношение слова успешно получено");
        } catch (Exception e) {
            logger.error("sendWordWithVoice Ошибка получения произношения " + e);
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
            logger.info("Произношение удачно отправлено");
        } catch (TelegramApiException e) {
            logger.error("Не удалось отправить произношение " + e);
        }

        sendMessage(message, textForMessage, getKeyboard(message.getChatId()));
    }

    /*Отправка обычных тектовых сообщений.*/
    public void sendMessage(Message message, String text) {
        sendMessage(message, text, false);
    }

    /*Отправка обычных текcтовых сообщений с привязкой клавиатуры.*/
    public void sendMessage(Message message, String text, InlineKeyboardMarkup inlineKeyboardMarkup) {
        logger.info("Начало формирования объекта SendMessage");
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        logger.info("Все подготовки к отправке сообщения произведены");
        sendMsg(sendMessage);
    }

    public void sendMessage(Message message, String text, boolean setReplyToMessageId) {
        logger.info("Начало формирования объекта SendMessage");
        SendMessage sendMessage = new SendMessage();
        sendMessage.setText(text);
        sendMessage.setChatId(message.getChatId());

        if (setReplyToMessageId) {
            sendMessage.setReplyToMessageId(message.getMessageId());
            logger.info("Отметка сообщения на которую отвечает выбрана");
        }

        setButtons(sendMessage);
        logger.info("Все подготовки к отправке сообщения произведены");
        sendMsg(sendMessage);
    }

    public void sendMsg(@NotNull SendMessage sendMessage) {
        sendMessage.enableMarkdown(true);
        sendMessage.enableHtml(true);
        try {
            execute(sendMessage);
            logger.info("Cообщение отправлено пользователю");
        } catch (TelegramApiException e) {
            logger.error("sendMsg Ошибка отправки сообщения пользователю " + e);
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

    private @NotNull InlineKeyboardMarkup getKeyboard(Long chatId) {
        return getKeyboard(chatId, true);
    }

    /*    Добавление клавиатуры под сообщение. параментр boolean определяет будет ли первая строчка в клавиатуре*/
    private InlineKeyboardMarkup getKeyboard(Long chatId, boolean firstLineInKeyboard) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());
        keyboard.add(new ArrayList<>());

        if (firstLineInKeyboard) {
            InlineKeyboardButton delete = new InlineKeyboardButton("❌ Удалить это слово");
            delete.setCallbackData("delete");
            keyboard.get(0).add(delete);

            String userMenu = BotsUser.getUserMenu(chatId);

            if (userMenu.equalsIgnoreCase("learning")) {
                InlineKeyboardButton learned = new InlineKeyboardButton("\uD83E\uDDE0 Уже знаю это слово");
                learned.setCallbackData("learned");
                keyboard.get(0).add(learned);
                logger.info("В клавиатуру добавлена кнопка \uD83E\uDDE0 Уже знаю это слово");
            } else if (userMenu.equalsIgnoreCase("repetition")) {
                InlineKeyboardButton forgot = new InlineKeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Снова изучать это слово");
                forgot.setCallbackData("forgot");
                keyboard.get(0).add(forgot);
                logger.info("В клавиатуру добавлена кнопка \uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Снова изучать это слово");
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