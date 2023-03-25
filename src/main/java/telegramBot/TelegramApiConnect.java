package telegramBot;

import Exceptions.TranslationException;
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
import telegramBot.user.WordsInDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*Данный класс подключается к телеграмм боту, принимает обновления,
распределяет задачи и отправляет сообщения пользователям*/
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

    /*Обработка нажатий на клавиши команд*/
    private void handleCallback(@NotNull CallbackQuery callbackQuery) {
        logger.info("Начало обработки запроса в нажатие клавиши");
        Message message = callbackQuery.getMessage();
        Long userId = message.getChatId();
        String data = callbackQuery.getData();
        String text = callbackQuery.getMessage().getText();


        switch (data) {
            case ("remembered") -> {
                logger.info("Принят запрос \"Я Вспомнил это слово\"");
                WordsInDatabase.updateWordProgress(userId, Word.getWord(text));
                editKeyboardAfterLeanedOrForgot(callbackQuery);
            }
            case ("forgot") -> {
                logger.info("Принят запрос \"Я Вспомнил это слово\"");
                editKeyboardAfterLeanedOrForgot(callbackQuery);
            }
            case ("context") -> {
                logger.info("Принят запрос \"На получение контекста\"");
                Word word = Word.getWord(message.getText());
                sendMessage(message, word.getContext());
            }
            case ("next") -> {
                logger.info("Принят запрос на следующее слово");
                getRandomWordAndSendToUser(message);
            }
            case ("yes") -> {
                String userMenu = BotsUser.getUserMenu(message.getChatId());
                assert userMenu != null;
                if (userMenu.equalsIgnoreCase("inDeleteMenu")) {
                    Word word = Word.getWord(message.getText());
                    word.deleteWordFromUserList(userId);
                    deleteInlineKeyboard(callbackQuery);
                    editMessageText(userId, message.getMessageId(), "Слово успешно удалено");
                } else {
                    deleteInlineKeyboard(callbackQuery);
                    editMessageText(userId, message.getMessageId(), "Попробуйте снова");
                }
            }
            case ("no") -> {
                deleteInlineKeyboard(callbackQuery);
            }
        }
    }

    /*Обработка текстовых команд или в случае, если пользователь присылает слова на добавление в словарь*/
    private void handleTextMessage(@NotNull Message message) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("handleTextMessage ", message);

        String InputMessageText = message.getText().trim();
        Long userId = message.getChatId();

        switch (InputMessageText) {
            case ("/start") -> sendMessage(message, "Добро пожаловать в наш бот по изучению английских слов.");
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
            case ("\uD83D\uDD00 Смешанный режим") -> {
                BotsUser.setMenu(userId, "mixed");
                getRandomWordAndSendToUser(message);
            }
            case ("\uD83D\uDCD3 Список изучаемых слов") -> {
                BotsUser.setMenu(userId, "AllFalse");
                String messageText = WordsInDatabase.getWordList(userId, "learning");
                if (messageText == null) messageText = "В вашем словаре нет слов на изучении";
                sendMessage(message, messageText);
            }
            case ("\uD83D\uDCD3 Список слов на повторении") -> {
                BotsUser.setMenu(userId, "AllFalse");
                String messageText = WordsInDatabase.getWordList(userId, "repetition");
                if (messageText == null) messageText = "В вашем словаре нет слов на повторении";
                sendMessage(message, messageText);
            }
            case ("\uD83D\uDCC8 Статистика") -> {
                BotsUser.setMenu(userId, "AllFalse");
                sendMessage(message, BotsUser.getStatistic(userId));
            }
            case ("/delete") -> {
                BotsUser.setMenu(userId, "inDeleteMenu");
                sendMessage(message, "Отправьте в виде сообщения слово, которое вы хотите удалить из вашего словаря!");
            }
            default -> {
                String menu = BotsUser.getUserMenu(userId);
                assert menu != null;
                switch (menu) {
                    case ("inAddMenu") -> {
                        try {
                            sendMessage(message, Word.add(InputMessageText, userId), true);
                        } catch (TranslationException e) {
                            sendMessage(message, "К сожалению нам не удалось корректно перевести данное слово. " +
                                    "Сообщение об ошибке уже отправлено администратору. В скором времени ошибка будет исправлена. " +
                                    "Эта ошибка не помешает вам добавлять и изучать другие слова");
                            throw new RuntimeException(e);
                        }
                    }
                    case ("inDeleteMenu") -> {
                        sendMessage(message, "Уверены ли вы, что хотите удалить данное слово?");
                        for (Word word : Word.getWordList(message.getText())) {
                            if (word.checkWordInUserList()) {
                                sendMessage(message, word.getEnWord() + "  -  " + word.getRuWord(), yesOrNoKeyboard());
                            }
                        }
                    }
                }
            }
        }
    }

    private void deleteInlineKeyboard(CallbackQuery callbackQuery) {
        logger.info("Начало удаления клавиатуры");

        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(callbackQuery.getMessage().getChatId());
        editMessageReplyMarkup.setMessageId(callbackQuery.getMessage().getMessageId());

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        inlineKeyboardMarkup.setKeyboard(new ArrayList<>());

        editMessageReplyMarkup.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(editMessageReplyMarkup);
            logger.info("Удаление клавиатуры отправлено");
        } catch (TelegramApiException e) {
            logger.error("deleteInlineKeyboard Ошибка отправки удаления клавиатуры");
            throw new RuntimeException(e);
        }
    }

    /*Изменение клавиатуры после смены словаря*/
    private void editKeyboardAfterLeanedOrForgot(CallbackQuery callbackQuery) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("editKeyboardAfterLeanedOrForgot", callbackQuery);

        logger.info("Начало редактирования клавиатуры под сообщениями");
        Message message = callbackQuery.getMessage();
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(message.getChatId());
        editMessageReplyMarkup.setMessageId(message.getMessageId());

        InlineKeyboardMarkup keyboard = getKeyboardOnlyWishNext(message.getChatId());

        editMessageReplyMarkup.setReplyMarkup(keyboard);

        try {
            execute(editMessageReplyMarkup);
            logger.info("Изменения клавиатуры отправлены");
        } catch (TelegramApiException e) {
            logger.error("editKeyboardAfterLeanedOrForgot Ошибка отправки изменений клавиатуры");
            throw new RuntimeException(e);
        }
    }

    /*Получение случайного слова из БД и отправка пользователю*/
    private void getRandomWordAndSendToUser(@NotNull Message message) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getRandomWordAndSendToUser", message);
        Long userId = message.getChatId();
        String menu = BotsUser.getUserMenu(userId);

        if (menu == null) {
            logger.error("getRandomWordAndSendToUser Меню из БД вернулось null");
            sendMessage(message, "Что-то пошло не так. Мы сообщили об этом Администратору. Скоро все исправим!");
            return;
        } else if (!(menu.equals("learning") || menu.equals("repetition") || menu.equals("mixed"))) {
            sendMessage(message, "Вы не выбрали меню. Пожалуйста выбери меню изучения или повторения слов");
            return;
        }

        Word word = Word.getRandomWord(userId);

        if (word == null) {
            switch (menu) {
                case ("learning") -> {
                    sendMessage(message, "У вас нет слов для изучения в данный момент. Пожалуйста, " +
                            "добавьте новые слова, или воспользуйтесь нашим банком слов.");
                    return;
                }
                case ("repetition") -> {
                    sendMessage(message, "У вас нет слов на повторении в данный момент. Пожалуйста, " +
                            "воспользуйтесь меню \"\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова\"");
                    return;
                }
                case ("mixed") -> {
                    sendMessage(message, "У вас нет слов для изучения или повторения в данный момент. Пожалуйста, " +
                            "добавьте новые слова, или воспользуйтесь нашим банком слов.");
                    return;
                }
            }
        }

        sendWordWithVoice(word, message);
    }


    /*Данный метод отправляет пользователю слово с произношением. В случае невозможность получить аудио файл с произношением
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

    /*Отправка обычных текстовых сообщений.*/
    public void sendMessage(Message message, String text) {
        sendMessage(message, text, false);
    }

    /*Отправка обычных текстовых сообщений с привязкой клавиатуры.*/
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
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("setButtons", sendMessage);
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
        keyboardThirdRow.add(new KeyboardButton("\uD83D\uDD00 Смешанный режим"));
        keyboardThirdRow.add(new KeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова"));

        keyboardRowList.add(keyboardFirstRow);
        keyboardRowList.add(keyboardSecondRow);
        keyboardRowList.add(keyboardThirdRow);

        replyKeyboardMarkup.setKeyboard(keyboardRowList);
        logger.info("Нижние кнопки успешно прикреплены к сообщению");
    }

    /*    Добавление клавиатуры под сообщение. Параметр boolean определяет, будет ли первая строчка в клавиатуре*/
    private InlineKeyboardMarkup getKeyboard(Long chatId) {
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getKeyboard", chatId);
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());

        InlineKeyboardButton remembered = new InlineKeyboardButton("\uD83D\uDE04 Я вспомнил это слово");
        remembered.setCallbackData("remembered");
        keyboard.get(0).add(remembered);

        InlineKeyboardButton context = new InlineKeyboardButton("Контекст");
        context.setCallbackData("context");
        keyboard.get(0).add(context);

        InlineKeyboardButton forgot = new InlineKeyboardButton("\uD83D\uDE14 Я не вспомнил это слово");
        forgot.setCallbackData("forgot");
        keyboard.get(0).add(forgot);

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        logger.info("Клавиатура под сообщения готова");
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup yesOrNoKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());

        InlineKeyboardButton yes = new InlineKeyboardButton("✅");
        yes.setCallbackData("yes");
        keyboard.get(0).add(yes);

        InlineKeyboardButton no = new InlineKeyboardButton("⛔️");
        no.setCallbackData("no");
        keyboard.get(0).add(no);

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        logger.info("Клавиатура под сообщения готова");
        return keyboardMarkup;
    }


    public void editMessageText(Long chatId, Integer messageId, String newText) {
        EditMessageText editMessage = new EditMessageText();
        // задаем параметры изменяемого сообщения
        editMessage.setChatId(chatId);
        editMessage.setMessageId(messageId);
        // задаем новый текст сообщения
        editMessage.setText(newText);

        try {
            // отправляем измененное сообщение
            execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup getKeyboardOnlyWishNext(Long chatId) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());

        InlineKeyboardButton next = new InlineKeyboardButton("➡️ Следующее слово");
        next.setCallbackData("next");
        keyboard.get(0).add(next);

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        logger.info("Клавиатура под сообщения готова c одной клавишей next готова");
        return keyboardMarkup;
    }

    @Override
    public String getBotUsername() {
        //return "SturviTestBot"; // test bot name
        return "@WordLeaningBot";
    }

    @Override
    public String getBotToken() {
       // return "5857743410:AAHyinYvlTc-grG76012Nqj6Of5SGNgmMvE"; // test token
        return "5915434126:AAHto2nUM8S1a9cb2Fgxz8F3P45BV4QGp7U";
    }
}