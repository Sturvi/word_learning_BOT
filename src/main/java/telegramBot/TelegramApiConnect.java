package telegramBot;

import Exceptions.TranslationException;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*Данный класс подключается к телеграмм боту, принимает обновления,
распределяет задачи и отправляет сообщения пользователям*/
public class TelegramApiConnect extends TelegramLongPollingBot {

    private static final Logger logger = Logger.getLogger(TelegramApiConnect.class);
    private String apiKey;
    private String botName;


    /**
     * Метод onUpdateReceived вызывается при получении обновления от пользователя.
     * Обрабатывает текстовые сообщения и обратные вызовы (callback) в зависимости от типа обновления.
     *
     * @param update объект обновления, полученный от пользователя
     */
    @Override
    public void onUpdateReceived(Update update) {
        try {
            logger.info("Пришел новый запрос от пользователя");

            BotUser user = BotUser.getBotUser(update);

            if (user.callbackQueryIsNull()) {
                handleTextMessage(user);
            } else {
                handleCallback(user);
            }
        } catch (Exception e) {
            logger.error("ГДЕТО ОШИБКА! " + e + " " + update);
        }
    }

    /*Обработка нажатий на клавиши команд*/
    private void handleCallback(@NotNull BotUser user) {
        logger.info("Начало обработки запроса в нажатие клавиши");

        String data = user.getCallbackQuery().getData();
        String text = user.getMessage().getText();
        String userMenu = user.getUserMenu();
        assert userMenu != null;

        switch (data) {
            case ("remembered") -> {
                logger.info("Принят запрос \"Я Вспомнил это слово\"");
                user.updateUserWordProgress(Word.getWord(text));
                editKeyboardAfterLeanedOrForgot(user);
            }
            case ("forgot") -> {
                logger.info("Принят запрос \"Я Вспомнил это слово\"");
                editKeyboardAfterLeanedOrForgot(user);
            }
            case ("context") -> {
                logger.info("Принят запрос \"На получение контекста\"");
                Word word = Word.getWord(text);
                sendMessage(user, word.getContextOrUsageExamples("context"));
            }
            case ("example") -> {
                logger.info("Принят запрос \"На получение примера использования\"");
                Word word = Word.getWord(user.getMessage().getText());
                sendMessage(user, word.getContextOrUsageExamples("usage_examples"));
            }
            case ("next") -> {
                logger.info("Принят запрос на следующее слово");
                getRandomWordAndSendToUser(user);
            }
            case ("yes") -> {
                logger.info("Принят запрос yes");
                switch (userMenu) {
                    case ("inDeleteMenu") -> {
                        logger.info("Принят запрос yes в inDeleteMenu");
                        Word word = Word.getWord(user.getMessage().getText());
                        word.deleteWordFromUserList(user);
                        deleteInlineKeyboard(user);
                        editMessageText(user, "Слово успешно удалено");
                    }
                    case ("inAddMenu") -> {
                        try {
                            logger.info("Принят запрос yes в inAddMenu");
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
            case ("no") -> {
                deleteInlineKeyboard(user);
            }
            case ("translator") -> {
                logger.info("Принят запрос \"Послать слово в переводчик\"");
                if (userMenu.equals("inAddMenu")) {
                    deleteInlineKeyboard(user);
                    String wordForTranslator = text.replaceAll("^.*?\"(.+?)\".*$", "$1");
                    Word word;
                    try {
                        var translatorResult = Word.addNewWordToDBFromTranslator(wordForTranslator, new HashSet<Integer>());
                        word = Word.getWord(translatorResult.get(0) + "  -  " + translatorResult.get(1));
                        Api.moderation(wordForTranslator, word, user);
                    } catch (TranslationException e) {
                        sendMessage(user, "К сожалению нам вернулся некорректный перевод из Гугл Переводчика. " +
                                "Сообщение об ошибке выслано администратору. Скоро ошибка будет исправлена. " +
                                "Эта ошибка не помешает вам изучать другие слова");
                        throw new RuntimeException(e);
                    }
                    sendMessage(user, "Результат полученный из Google Translator:");
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
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("handleTextMessage ", user);

        String inputMessageText = user.getMessage().getText().trim();

        switch (inputMessageText) {
            case ("/start") -> sendMessage(user, "Добро пожаловать в наш бот по изучению английских слов.");
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
                logger.info("Начало обработки \uD83D\uDCD6 Добавить случайные слова");
                user.setMenu("inAddMenu");
                var wordIdSet = Word.getRandomNewWordSet(user);

                if (wordIdSet.isEmpty()) {
                    logger.error("wordIdSet вернулся пустой");
                    sendMessage(user, "Извините, произошла непредвиденная ошибка. Мы работаем над исправлением");
                }

                sendMessage(user, "Выберите слова, которые хотите добавить в свой словарь:");
                for (Integer wordId : wordIdSet) {
                    Word word = Word.getWord(wordId);
                    sendMessage(user, word.toString(), yesOrNoKeyboard());
                }
                logger.info("Слова успешно предложены");
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
                        ArrayList<Word> wordArrayList = Word.getWordList(inputMessageText);

                        if (!wordArrayList.isEmpty()) {
                            sendMessage(user, "Уверены ли вы, что хотите удалить данное слово?");
                        } else {
                            sendMessage(user, "Данного слова не обнаружено в вашем словаре");
                        }

                        for (Word word : wordArrayList) {
                            if (word.checkWordInUserList()) {
                                sendMessage(user, word.getEnWord() + "  -  " + word.getRuWord(), yesOrNoKeyboard());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Метод deleteInlineKeyboard удаляет инлайн-клавиатуру из сообщения.
     * Создает объект EditMessageReplyMarkup, устанавливает идентификатор чата и сообщения,
     * устанавливает пустую клавиатуру и выполняет действие.
     *
     * @param user объект BotUser, содержащий информацию о пользователе
     */
    private void deleteInlineKeyboard(BotUser user) {
        logger.info("Начало удаления клавиатуры");

        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(user.getUserId());
        editMessageReplyMarkup.setMessageId(user.getMessage().getMessageId());

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        inlineKeyboardMarkup.setKeyboard(new ArrayList<>());

        editMessageReplyMarkup.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(editMessageReplyMarkup);
            logger.info("Удаление клавиатуры отправлено");
        } catch (TelegramApiException e) {
            logger.error("deleteInlineKeyboard Ошибка отправки удаления клавиатуры" + e);
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
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("editKeyboardAfterLeanedOrForgot", user);

        logger.info("Начало редактирования клавиатуры под сообщениями");
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
            logger.info("Изменения клавиатуры отправлены");
        } catch (TelegramApiException e) {
            logger.error("editKeyboardAfterLeanedOrForgot Ошибка отправки изменений клавиатуры");
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
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("getRandomWordAndSendToUser", user);
        String menu = user.getUserMenu();

        if (menu == null) {
            logger.error("getRandomWordAndSendToUser Меню из БД вернулось null");
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
        NullCheck nullCheck = () -> logger;
        nullCheck.checkForNull("sendWordWithVoice ", word, user);
        String textForMessage = word.toStringRandomWithTranscription();

        File voice;
        try {
            // Получение файла с произношением слова
            voice = word.getVoice();
            logger.info("Произношение слова успешно получено");
        } catch (Exception e) {
            logger.error("sendWordWithVoice Ошибка получения произношения " + e);
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
            logger.info("Произношение удачно отправлено");
        } catch (TelegramApiException e) {
            logger.error("Не удалось отправить произношение " + e);
        }

        // Отправка сообщения со словом и клавиатурой
        sendMessage(user, textForMessage, getKeyboard(user.getUserId()));
    }

    /*Отправка обычных текстовых сообщений.*/
    public void sendMessage(BotUser user, String text) {
        sendMessage(user, text, false);
    }

    /*Отправка обычных текстовых сообщений с привязкой клавиатуры.*/
    public void sendMessage(BotUser user, String text, InlineKeyboardMarkup inlineKeyboardMarkup) {
        logger.info("Начало формирования объекта SendMessage");
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(user.getUserId());
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        logger.info("Все подготовки к отправке сообщения произведены");
        executeMessage(sendMessage);
    }

    /**
     * Отправка сообщения пользователю
     *
     * @param user                объект пользователя
     * @param text                текст сообщения
     * @param setReplyToMessageId флаг установки идентификатора сообщения для ответа
     */
    public void sendMessage(BotUser user, String text, boolean setReplyToMessageId) {
        logger.info("Начало формирования объекта SendMessage");
        SendMessage sendMessage = new SendMessage();
        sendMessage.setText(text);
        sendMessage.setChatId(user.getUserId());

        // Установка идентификатора сообщения для ответа
        if (setReplyToMessageId) {
            sendMessage.setReplyToMessageId(user.getMessage().getMessageId());
            logger.info("Отметка сообщения на которую отвечает выбрана");
        }

        // Установка кнопок
        setButtons(sendMessage);
        logger.info("Все подготовки к отправке сообщения произведены");
        executeMessage(sendMessage);
    }

    /**
     * Отправка сообщения пользователю
     *
     * @param sendMessage объект сообщения
     */
    public void executeMessage(@NotNull SendMessage sendMessage) {
        // Включение поддержки Markdown и HTML
        sendMessage.enableMarkdown(true);
        sendMessage.enableHtml(true);
        try {
            // Отправка сообщения
            execute(sendMessage);
            logger.info("Cообщение отправлено пользователю");
        } catch (TelegramApiException e) {
            logger.error("sendMsg Ошибка отправки сообщения пользователю " + e);
            e.printStackTrace();
        }
    }

    /*Нижние клавиши*/
    public void setButtons(SendMessage sendMessage) {
        logger.info("Старт метода TelegramApiConnect.setButton");
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
        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDCD6 Добавить случайные слова"));
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

        logger.info("Клавиатура под сообщения готова");
        return keyboardMarkup;
    }


    private InlineKeyboardMarkup sendToTranslatorButton() {
        logger.info("Метод sendToTranslatorButton стартовал");
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());

        InlineKeyboardButton yes = new InlineKeyboardButton("Получить перевод из Google Translator");
        yes.setCallbackData("translator");
        keyboard.get(0).add(yes);

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

    /**
     * Метод editMessageText изменяет текст сообщения в чате.
     * Задает параметры изменяемого сообщения, устанавливает новый текст и отправляет измененное сообщение.
     *
     * @param user    объект BotUser, содержащий информацию о пользователе
     * @param newText новый текст сообщения
     */
    public void editMessageText(BotUser user, String newText) {
        EditMessageText editMessage = new EditMessageText();
        // задаем параметры изменяемого сообщения
        editMessage.setChatId(user.getUserId());
        editMessage.setMessageId(user.getMessage().getMessageId());
        // задаем новый текст сообщения
        editMessage.setText(newText);

        try {
            // отправляем измененное сообщение
            execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    /*Метод возвращает InlineKeyboardMarkup с одной кнопкой "Следующее слово".
    Эта кнопка имеет callbackData "next".*/
    private InlineKeyboardMarkup getKeyboardOnlyWishNext() {
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