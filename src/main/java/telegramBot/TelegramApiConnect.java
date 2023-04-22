package telegramBot;

import Exceptions.TranslationException;
import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TelegramApiConnect extends TelegramLongPollingBot {

    private static final Logger LOGGER = Logger.getLogger(TelegramApiConnect.class);
    private final ExecutorService executorService;
    private String apiKey;
    private String botName;

    public TelegramApiConnect() {
        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * Метод onUpdateReceived вызывается при получении обновления от пользователя.
     * Обрабатывает текстовые сообщения и обратные вызовы (callback) в зависимости от типа обновления.
     *
     * @param update объект обновления, полученный от пользователя
     */
    @Override
    public void onUpdateReceived(Update update) {
        Runnable newUserRequest = () -> {
            try {
                LOGGER.info("Пришел новый запрос от пользователя");

                if (update.hasMyChatMember() && update.getMyChatMember().getNewChatMember().getStatus().equals("kicked")){
                    Long chatId = update.getMyChatMember().getFrom().getId();
                    BotUser.deactivateUser(chatId);
                    return;
                }

                BotUser user = BotUser.getBotUser(update);

                if (user.callbackQueryIsNull()) {
                    handleTextMessage(user);
                } else {
                    handleCallback(user);
                }
            } catch (Exception e) {
                LOGGER.error("ГДЕТО ОШИБКА! " + e + " " + update);
            }
        };

        // Используем пул потоков для выполнения задачи
        executorService.submit(newUserRequest);
    }

    /**
     * Обрабатывает обратные вызовы (callback) от нажатий на клавиши команд чат-бота.
     * В зависимости от данных обратного вызова, метод выполняет различные действия,
     * такие как изменение статуса слов, получение контекста или примеров использования и другие.
     *
     * @param user Объект BotUser, представляющий пользователя чат-бота.
     */
    private void handleCallback(@NotNull BotUser user) {
        LOGGER.info("Начало обработки запроса в нажатие клавиши");

        String data = user.getCallbackQuery().getData();
        String text = user.getMessage().getText();
        String userMenu = user.getUserMenu();
        assert userMenu != null;

        switch (data) {
            case ("remembered") -> {
                LOGGER.info("Принят запрос \"Я Вспомнил это слово\"");
                user.updateUserWordProgress(Word.getWord(text));
                editKeyboardAfterLeanedOrForgot(user);
            }
            case ("forgot") -> {
                LOGGER.info("Принят запрос \"Я Вспомнил это слово\"");
                editKeyboardAfterLeanedOrForgot(user);
            }
            case ("context") -> {
                LOGGER.info("Принят запрос \"На получение контекста\"");
                Word word = Word.getWord(text);
                sendMessage(user, word.getContextOrUsageExamples("context"));
            }
            case ("example") -> {
                LOGGER.info("Принят запрос \"На получение примера использования\"");
                Word word = Word.getWord(user.getMessage().getText());
                sendMessage(user, word.getContextOrUsageExamples("usage_examples"));
            }
            case ("next") -> {
                LOGGER.info("Принят запрос на следующее слово");
                getRandomWordAndSendToUser(user);
            }
            case ("yes") -> {
                LOGGER.info("Принят запрос yes");
                switch (userMenu) {
                    case ("inDeleteMenu") -> {
                        LOGGER.info("Принят запрос yes в inDeleteMenu");
                        Word word = Word.getWord(user.getMessage().getText());
                        word.deleteWordFromUserList(user);
                        deleteInlineKeyboard(user);
                        editMessageText(user, "Слово успешно удалено");
                    }
                    case ("inAddMenu") -> {
                        try {
                            LOGGER.info("Принят запрос yes в inAddMenu");
                            Word word = Word.getWord(user.getMessage().getText());
                            word.addNewWordsToUserDictionary(user);
                            deleteInlineKeyboard(user);
                            editMessageText(user, "Слово " + word + " добавлено в твой словарь");
                        } catch (IndexOutOfBoundsException e) {
                            deleteInlineKeyboard(user);
                            sendMessage(user, "Извините, но данное слово было удалено из Базы данных");
                        }
                    }
                    default -> deleteInlineKeyboard(user);
                }
            }
            case ("no") -> deleteInlineKeyboard(user);
            case ("translator") -> {
                LOGGER.info("Принят запрос \"Послать слово в переводчик\"");
                if (userMenu.equals("inAddMenu")) {
                    deleteInlineKeyboard(user);
                    String wordForTranslator = text.replaceAll("^.*?\"(.+?)\".*$", "$1");
                    Word word = null;
                    var wordId = new HashSet<Integer>();
                    try {
                        Word.addNewWordToDBFromTranslator(wordForTranslator, wordId);
                        for (Integer id : wordId) {
                            word = Word.getWord(id);
                        }
                        Api.moderation(wordForTranslator, word, user);
                    } catch (TranslationException e) {
                        sendMessage(user, "К сожалению нам вернулся некорректный перевод из Гугл Переводчика. " +
                                "Сообщение об ошибке выслано администратору. Скоро ошибка будет исправлена. " +
                                "Эта ошибка не помешает вам изучать другие слова");
                        throw new RuntimeException(e);
                    } catch (SQLException e) {
                        for (Integer id : wordId) {
                            word = Word.getWord(id);
                        }
                    }
                    sendMessage(user, "Результат полученный из Google Translator:");
                    assert word != null;
                    sendMessage(user, word.toString(), yesOrNoKeyboard());
                } else {
                    deleteInlineKeyboard(user);
                    sendMessage(user, "Вы не находитесь в меню добавления слов. Пожалуйста, выберите сначала необходимое меню");
                }
            }
        }
    }

    /**
     * Обрабатывает текстовые сообщения, отправленные пользователем в чат-боте на основе Telegram.
     * В зависимости от текста сообщения, метод выполняет различные действия, такие как добавление слов,
     * изучение слов, повторение слов и другие.
     *
     * @param user Объект BotUser, представляющий пользователя чат-бота.
     */
    private void handleTextMessage(@NotNull BotUser user) {
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("handleTextMessage ", user);

        String inputMessageText = user.getMessage().getText().trim();

        switch (inputMessageText) {
            case ("/start"), ("/help") -> {
                sendHelpText(user);
            }
            case ("/answer") -> {
                user.setMenu("inAnswerMenu");
                sendMessage(user, "Пришлите пожалуйста ваш вопрос. \n\nПримечание: получение ответа может занять некоторое время");
            }
            case ("\uD83D\uDDD2 Добавить слова") -> {
                user.setMenu("inAddMenu");
                sendMessage(user, """
                        Можете отправлять слова, которые хотите добавить в свою коллекцию.\s

                        Если нужно добавить несколько слов, можете отправлять их по очереди.

                        Можете отправлять также словосочетания

                        Учтите, что слова переводятся автоматически, с помощью сервисов онлайн перевода и никак не проходят дополнительные проверки орфографии. Поэтому даже при небольших ошибках, перевод также будет ошибочный.""");
            }
            case ("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова") -> {
                user.setMenu("learning");
                getRandomWordAndSendToUser(user);
            }
            case ("\uD83D\uDD01 Повторять слова") -> {
                user.setMenu("repetition");
                getRandomWordAndSendToUser(user);
            }
            case ("\uD83D\uDD00 Смешанный режим") -> {
                user.setMenu("mixed");
                getRandomWordAndSendToUser(user);
            }
            case ("\uD83D\uDCD3 Список изучаемых слов") -> {
                user.setMenu("AllFalse");
                var messagesText = Word.fetchUserWords(user, "learning");
                if (messagesText.isEmpty())
                    sendMessage(user, "В вашем словаре нет слов на изучении");
                else {
                    for (String messageText : messagesText) {
                        sendMessage(user, messageText);
                    }
                }
            }
            case ("\uD83D\uDCD3 Список слов на повторении") -> {
                user.setMenu("AllFalse");
                var messagesText = Word.fetchUserWords(user, "repetition");
                if (messagesText.isEmpty())
                    sendMessage(user, "В вашем словаре нет слов на повторении");
                else {
                    for (String messageText : messagesText) {
                        sendMessage(user, messageText);
                    }
                }
            }
            case ("\uD83D\uDCD6 Добавить случайные слова") -> {
                LOGGER.info("Начало обработки \uD83D\uDCD6 Добавить случайные слова");
                user.setMenu("inAddMenu");
                var wordIdSet = Word.getRandomNewWordSet(user);

                if (wordIdSet.isEmpty()) {
                    LOGGER.error("wordIdSet вернулся пустой");
                    sendMessage(user, "Извините, произошла непредвиденная ошибка. Мы работаем над исправлением");
                }

                sendMessage(user, "Выберите слова, которые хотите добавить в свой словарь:");
                for (Integer wordId : wordIdSet) {
                    Word word = Word.getWord(wordId);
                    sendMessage(user, word.toString(), yesOrNoKeyboard());
                }
                LOGGER.info("Слова успешно предложены");
            }
            case ("/statistic") -> {
                user.setMenu("AllFalse");
                sendMessage(user, user.getStatistic());
            }
            case ("/delete") -> {
                user.setMenu("inDeleteMenu");
                sendMessage(user, "Отправьте в виде сообщения слово, которое вы хотите удалить из вашего словаря!");
            }
            default -> {
                String menu = user.getUserMenu();
                assert menu != null;
                switch (menu) {
                    case ("inAddMenu") -> {
                        Set<Integer> wordIdList = null;
                        try {
                            wordIdList = Word.add(inputMessageText, user);
                        } catch (TranslationException e) {
                            sendMessage(user, "К сожалению нам вернулся некорректный перевод из Гугл Переводчика. " +
                                    "Сообщение об ошибке выслано администратору. Одна из возможных причин ошибки " +
                                    "может быть в том, что слово набрано с ошибкой.");
                        }

                        assert wordIdList != null;
                        if (wordIdList.isEmpty()) {
                            sendMessage(user, "Данное слово (или словосочетание) уже находятся в твоем словаре");
                            sendMessage(user, "Нужен другой перевод слова \"" + inputMessageText + "\" ?", sendToTranslatorButton());
                            return;
                        }

                        sendMessage(user, "Добавить?..");
                        for (Integer wordId : wordIdList) {
                            Word word = Word.getWord(wordId);
                            sendMessage(user, word.toString(), yesOrNoKeyboard());
                        }
                        sendMessage(user, "Нет нужного перевода слова \"" + inputMessageText + "\" ?", sendToTranslatorButton());
                    }
                    case ("inDeleteMenu") -> {
                        ArrayList<Word> wordArrayList = Word.getWordList(user, inputMessageText);

                        if (!wordArrayList.isEmpty()) {
                            sendMessage(user, "Уверены ли вы, что хотите удалить данное слово?");
                        } else {
                            sendMessage(user, "Данного слова не обнаружено в вашем словаре");
                        }

                        for (Word word : wordArrayList) {
                            sendMessage(user, word.getEnWord() + "  -  " + word.getRuWord(), yesOrNoKeyboard());
                        }
                    }
                    case ("inAnswerMenu") -> {
                        LOGGER.info("Получен запрос на получение ответа");
                        Message newMessage = sendMessage(user, "Формируется ответ на ваш вопрос. Пожалуйста ожидайте...");
                        LOGGER.info("Отправка предварительного сообщения отправлена");

                        try {
                            String answer = Api.getEnglishLearningAnswer(inputMessageText);
                            LOGGER.info("Получен ответ от Chat GPT " + answer);
                            deleteMessage(newMessage.getChatId(), newMessage.getMessageId());
                            sendMessage(user, answer);
                            Api.addQuestionToDataBase(user.getUserId(), inputMessageText, answer);
                            LOGGER.info("Отправлен ответ пользователю");
                        } catch (Exception e) {
                            LOGGER.error("Ошибка отправления ответа " + e);
                            deleteMessage(newMessage.getChatId(), newMessage.getMessageId());
                            sendMessage(user, "Произошла ошибка при генерации ответа. Попробуйте снова. Если ошибка повторится вновь рекомендуется повторить попытку через некоторое время");
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Метод deleteInlineKeyboard удаляет клавиатуру из сообщения.
     * Создает объект EditMessageReplyMarkup, устанавливает идентификатор чата и сообщения,
     * устанавливает пустую клавиатуру и выполняет действие.
     *
     * @param user объект BotUser, содержащий информацию о пользователе
     */
    private void deleteInlineKeyboard(@NotNull BotUser user) {
        LOGGER.info("Начало удаления клавиатуры");

        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(user.getUserId());
        editMessageReplyMarkup.setMessageId(user.getMessage().getMessageId());

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        inlineKeyboardMarkup.setKeyboard(new ArrayList<>());

        editMessageReplyMarkup.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(editMessageReplyMarkup);
            LOGGER.info("Удаление клавиатуры отправлено");
        } catch (TelegramApiException e) {
            LOGGER.error("deleteInlineKeyboard Ошибка отправки удаления клавиатуры" + e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Изменение клавиатуры после смены словаря
     *
     * @param user объект пользователя
     */
    private void editKeyboardAfterLeanedOrForgot(BotUser user) {
        // Проверка на null значения
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("editKeyboardAfterLeanedOrForgot", user);

        LOGGER.info("Начало редактирования клавиатуры под сообщениями");
        Integer messageId = user.getMessage().getMessageId();
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(user.getUserId());
        editMessageReplyMarkup.setMessageId(messageId);

        // Получение клавиатуры только с кнопкой "Next"
        InlineKeyboardMarkup keyboard = getKeyboardOnlyWishNext();

        editMessageReplyMarkup.setReplyMarkup(keyboard);

        try {
            // Отправка изменений клавиатуры
            execute(editMessageReplyMarkup);
            LOGGER.info("Изменения клавиатуры отправлены");
        } catch (TelegramApiException e) {
            LOGGER.error("editKeyboardAfterLeanedOrForgot Ошибка отправки изменений клавиатуры");
            throw new RuntimeException(e);
        }
    }

    /**
     * Метод getRandomWordAndSendToUser получает случайное слово из БД в соответствии с выбранным пользователем меню
     * и отправляет его пользователю с голосовым сообщением.
     *
     * @param user объект BotUser, содержащий информацию о пользователе
     */
    private void getRandomWordAndSendToUser(@NotNull BotUser user) {
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("getRandomWordAndSendToUser", user);
        String menu = user.getUserMenu();

        if (menu == null) {
            LOGGER.error("getRandomWordAndSendToUser Меню из БД вернулось null");
            sendMessage(user, "Что-то пошло не так. Мы сообщили об этом Администратору. Скоро все исправим!");
            return;
        } else if (!(menu.equals("learning") || menu.equals("repetition") || menu.equals("mixed"))) {
            sendMessage(user, "Вы не выбрали меню. Пожалуйста выбери меню изучения или повторения слов");
            return;
        }

        Word word = Word.getRandomWordFromUserDictionary(user);

        if (word == null) {
            switch (menu) {
                case ("learning") -> {
                    sendMessage(user, "У вас нет слов для изучения в данный момент. Пожалуйста, " +
                            "добавьте новые слова, или воспользуйтесь нашим банком слов.");
                    return;
                }
                case ("repetition") -> {
                    sendMessage(user, "У вас нет слов на повторении в данный момент. Пожалуйста, " +
                            "воспользуйтесь меню \"\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова\"");
                    return;
                }
                case ("mixed") -> {
                    sendMessage(user, "У вас нет слов для изучения или повторения в данный момент. Пожалуйста, " +
                            "добавьте новые слова, или воспользуйтесь нашим банком слов.");
                    return;
                }
            }
        }

        sendWordWithVoice(word, user);
    }

    /**
     * Данный метод отправляет пользователю слово с произношением. В случае невозможность получить аудио файл с произношением
     * отправляет просто слово
     *
     * @param word объект слова
     * @param user объект пользователя
     */
    private void sendWordWithVoice(Word word, BotUser user) {
        // Проверка на null значения
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("sendWordWithVoice ", word, user);
        String textForMessage = word.toStringRandomWithTranscription(user);

        File voice;
        try {
            // Получение файла с произношением слова
            voice = word.getVoice();
            LOGGER.info("Произношение слова успешно получено");
        } catch (Exception e) {
            LOGGER.error("sendWordWithVoice Ошибка получения произношения " + e);
            sendMessage(user, textForMessage, getKeyboard(user.getUserId()));
            return;
        }

        InputFile inputFile = new InputFile(voice);
        SendAudio audio = new SendAudio();
        audio.setTitle("Произношение слова");
        audio.setChatId(user.getUserId().toString());
        audio.setAudio(inputFile);

        try {
            // Отправка файла с произношением слова
            execute(audio);
            LOGGER.info("Произношение удачно отправлено");
        } catch (TelegramApiException e) {
            LOGGER.error("Не удалось отправить произношение " + e);
        }

        // Отправка сообщения со словом и клавиатурой
        sendMessage(user, textForMessage, getKeyboard(user.getUserId()));
    }

    /**
     * Отправляет текстовое сообщение.
     *
     * @param user Пользователь, которому нужно отправить сообщение.
     * @param text Текст сообщения.
     */
    public Message sendMessage(BotUser user, String text) {
        return sendMessage(user, text, false);
    }

    /**
     * Отправляет текстовое сообщение с привязкой клавиатуры.
     *
     * @param user                 Пользователь, которому нужно отправить сообщение.
     * @param text                 Текст сообщения.
     * @param inlineKeyboardMarkup Клавиатура для прикрепления к сообщению.
     */
    public void sendMessage(@NotNull BotUser user, String text, InlineKeyboardMarkup inlineKeyboardMarkup) {
        LOGGER.info("Начало формирования объекта SendMessage");
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(user.getUserId());
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        LOGGER.info("Все подготовки к отправке сообщения произведены");
        executeMessage(sendMessage);
    }

    /**
     * Отправка сообщения пользователю
     *
     * @param user                объект пользователя
     * @param text                текст сообщения
     * @param setReplyToMessageId флаг установки идентификатора сообщения для ответа
     */
    public Message sendMessage(@NotNull BotUser user, String text, boolean setReplyToMessageId) {
        LOGGER.info("Начало формирования объекта SendMessage");
        SendMessage sendMessage = new SendMessage();
        sendMessage.setText(text);
        sendMessage.setChatId(user.getUserId());

        // Установка идентификатора сообщения для ответа
        if (setReplyToMessageId) {
            sendMessage.setReplyToMessageId(user.getMessage().getMessageId());
            LOGGER.info("Отметка сообщения на которую отвечает выбрана");
        }

        // Установка кнопок
        setButtons(sendMessage);
        LOGGER.info("Все подготовки к отправке сообщения произведены");
        return executeMessage(sendMessage);
    }

    /**
     * Отправка сообщения пользователю
     *
     * @param sendMessage объект сообщения
     */
    public Message executeMessage(@NotNull SendMessage sendMessage) {
        // Включение поддержки Markdown и HTML
        sendMessage.enableMarkdown(true);
        sendMessage.enableHtml(true);

        Message message = null;
        try {
            // Отправка сообщения
            message = execute(sendMessage);
            LOGGER.info("Cообщение отправлено пользователю");
        } catch (TelegramApiException e) {
            LOGGER.error("sendMsg Ошибка отправки сообщения пользователю " + e);
            e.printStackTrace();
        }

        return message;
    }

    /**
     * Устанавливает кнопки для сообщения.
     *
     * @param sendMessage Сообщение, к которому нужно прикрепить кнопки.
     */
    public void setButtons(SendMessage sendMessage) {
        LOGGER.info("Старт метода TelegramApiConnect.setButton");
        NullCheck nullCheck = () -> LOGGER;
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

        // Добавление кнопок в первую строку
        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDDD2 Добавить слова"));
        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDCD6 Добавить случайные слова"));

        // Добавление кнопок во вторую строку
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDCD3 Список слов на повторении"));
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDCD3 Список изучаемых слов"));

        // Добавление кнопок в третью строку
        keyboardThirdRow.add(new KeyboardButton("\uD83D\uDD01 Повторять слова"));
        keyboardThirdRow.add(new KeyboardButton("\uD83D\uDD00 Смешанный режим"));
        keyboardThirdRow.add(new KeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова"));

        // Добавление строк с кнопками в список
        keyboardRowList.add(keyboardFirstRow);
        keyboardRowList.add(keyboardSecondRow);
        keyboardRowList.add(keyboardThirdRow);

        // Установка списка строк с кнопками для клавиатуры
        replyKeyboardMarkup.setKeyboard(keyboardRowList);

        LOGGER.info("Нижние кнопки успешно прикреплены к сообщению");
    }

    /**
     * Метод создает и возвращает клавиатуру с кнопками для сообщения.
     *
     * @param chatId идентификатор чата
     * @return клавиатура с кнопками для сообщения
     */
    private @NotNull InlineKeyboardMarkup getKeyboard(Long chatId) {
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("getKeyboard", chatId);
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());
        keyboard.add(new ArrayList<>());

        InlineKeyboardButton remembered = new InlineKeyboardButton("✅ Вспомнил");
        remembered.setCallbackData("remembered");
        keyboard.get(1).add(remembered);

        InlineKeyboardButton context = new InlineKeyboardButton("Контекст");
        context.setCallbackData("context");
        keyboard.get(1).add(context);

        InlineKeyboardButton example = new InlineKeyboardButton("Пример использования");
        example.setCallbackData("example");
        keyboard.get(0).add(example);

        InlineKeyboardButton forgot = new InlineKeyboardButton("⛔️ Не вспомнил");
        forgot.setCallbackData("forgot");
        keyboard.get(1).add(forgot);

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        LOGGER.info("Клавиатура под сообщения готова");
        return keyboardMarkup;
    }

    /**
     * Метод создает и возвращает клавиатуру с кнопкой для отправки запроса в Google Translator.
     *
     * @return клавиатура с кнопкой для отправки запроса в Google Translator
     */
    private @NotNull InlineKeyboardMarkup sendToTranslatorButton() {
        LOGGER.info("Метод sendToTranslatorButton стартовал");
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());

        InlineKeyboardButton yes = new InlineKeyboardButton("Получить перевод из Google Translator");
        yes.setCallbackData("translator");
        keyboard.get(0).add(yes);

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        LOGGER.info("Клавиатура под сообщения готова");
        return keyboardMarkup;
    }

    /**
     * Метод, который возвращает InlineKeyboardMarkup с двумя кнопками: "✅" и "⛔️".
     * Каждая кнопка имеет соответствующий callbackData: "yes" и "no".
     *
     * @return объект класса InlineKeyboardMarkup с двумя кнопками: "✅" и "⛔️"
     */
    private @NotNull InlineKeyboardMarkup yesOrNoKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Создание пустой строки клавиатуры
        keyboard.add(new ArrayList<>());

        // Создание кнопки "✅" с callbackData "yes" и добавление её в строку клавиатуры
        InlineKeyboardButton yes = new InlineKeyboardButton("✅");
        yes.setCallbackData("yes");
        keyboard.get(0).add(yes);

        // Создание кнопки "⛔️" с callbackData "no" и добавление её в строку клавиатуры
        InlineKeyboardButton no = new InlineKeyboardButton("⛔️");
        no.setCallbackData("no");
        keyboard.get(0).add(no);

        // Создание объекта класса InlineKeyboardMarkup и добавление строки клавиатуры в него
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        LOGGER.info("Клавиатура под сообщения готова");
        return keyboardMarkup;
    }

    /**
     * Метод editMessageText обновляет текст сообщения в чате.
     * Устанавливает параметры редактируемого сообщения, устанавливает новый текст и отправляет обновленное сообщение.
     *
     * @param userId    идентификатор пользователя BotUser, содержащий информацию о пользователе
     * @param messageId идентификатор сообщения, которое нужно отредактировать
     * @param newText   новый текст сообщения
     */
    public void editMessageText(Long userId, Integer messageId, String newText) {
        EditMessageText editMessage = new EditMessageText();
        // устанавливаем параметры редактируемого сообщения
        editMessage.setChatId(userId);
        editMessage.setMessageId(messageId);
        // устанавливаем новый текст сообщения
        editMessage.setText(newText);

        try {
            // отправляем обновленное сообщение
            execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Перегруженный метод editMessageText для удобства использования с объектом BotUser.
     * Вызывает основной метод editMessageText с параметрами userId и messageId, извлеченными из объекта BotUser.
     *
     * @param user    объект BotUser, содержащий информацию о пользователе
     * @param newText новый текст сообщения
     */
    public void editMessageText(@NotNull BotUser user, String newText){
        editMessageText(user.getUserId(), user.getMessage().getMessageId(), newText);
    }

    /**
     * Удаляет сообщение с заданным идентификатором из чата с заданным идентификатором пользователя.
     *
     * @param userId Идентификатор пользователя, чей чат содержит сообщение.
     * @param messageId Идентификатор удаляемого сообщения.
     */
    private void deleteMessage(Long userId, Integer messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(userId);
        deleteMessage.setMessageId(messageId);

        try {
            execute(deleteMessage);
            LOGGER.info("Сообщение с идентификатором " + messageId + " удалено из чата с пользователем с идентификатором " + userId);
        } catch (TelegramApiException e) {
            LOGGER.error("Ошибка при удалении сообщения с идентификатором " + messageId + " из чата с пользователем с идентификатором " + userId + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void sendHelpText(BotUser user) {
        String helpText = "";
        String sql = "Select message from message_templates where template_name = 'help'";

        try (PreparedStatement preparedStatement = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()){
                helpText = resultSet.getString("message");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        sendMessage(user, helpText);
    }

    /**
     * Метод, который возвращает InlineKeyboardMarkup с одной кнопкой "Следующее слово".
     * Эта кнопка имеет callbackData "next".
     *
     * @return объект класса InlineKeyboardMarkup с одной кнопкой "Следующее слово"
     */
    private @NotNull InlineKeyboardMarkup getKeyboardOnlyWishNext() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Создание пустой строки клавиатуры
        keyboard.add(new ArrayList<>());

        // Создание кнопки "Следующее слово" с callbackData "next" и добавление её в строку клавиатуры
        InlineKeyboardButton next = new InlineKeyboardButton("➡️ Следующее слово");
        next.setCallbackData("next");
        keyboard.get(0).add(next);

        // Создание объекта класса InlineKeyboardMarkup и добавление строки клавиатуры в него
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        LOGGER.info("Клавиатура под сообщения готова c одной клавишей next готова");
        return keyboardMarkup;
    }

    /**
     * Метод, который возвращает имя бота. Если имя не задано, оно получается из Api с помощью ключа "test_telegram_name".
     *
     * @return имя бота
     */
    @Override
    public String getBotUsername() {
        if (botName == null) botName = Api.getApiKey("test_telegram_name");
        return botName;
    }

    /**
     * Возвращает токен бота, используемый для авторизации в Telegram API.
     * Если токен еще не был установлен, метод получает его из API с использованием имени бота.
     *
     * @return токен бота
     */
    @Override
    public String getBotToken() {
        if (apiKey == null) apiKey = Api.getApiKey("test_telegram");
        return apiKey;
    }
}