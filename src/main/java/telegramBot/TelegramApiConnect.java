package telegramBot;

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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*Данный класс подключается к телеграмм боту, принимает обновления,
распледеляет задачи и отправляет сообщения пользователям*/
public class TelegramApiConnect extends TelegramLongPollingBot {

    @Override
    public void onUpdateReceived(Update update) {

        Long chatId = update.getMessage() == null ? update.getCallbackQuery().getMessage().getChatId() : update.getMessage().getChatId();

        //Если команда пришла от админа, ее обработка уходит в класс Админ
        if (chatId.equals(Main.admin.getChatID())){
            Main.admin.inputCommand(update);
            return;
        }

        if (!telegramBot.Main.userMap.containsKey(chatId)) {
            telegramBot.Main.userMap.put(chatId, new User());
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        } else {
            handleTextMessage(update.getMessage());
        }

    }

    /*Оброботка нажаний на клавиши команд*/
    private void handleCallback(CallbackQuery callbackQuery) {
        Message message = callbackQuery.getMessage();
        User user = telegramBot.Main.userMap.get(message.getChatId());
        String data = callbackQuery.getData();
        String text = callbackQuery.getMessage().getText();


        switch (data) {
            case ("delete") -> {
                String[] texts = text.split(" - ");
                user.removeWord(texts[0].trim());
                editKeyboardAfterDeleteMessage(callbackQuery);
            }
            case ("next") -> {
                if (user.isInLeaningMenu()) {
                    getLeaningWord(user, message);
                } else if (user.isInRepeatMenu()) {
                    getRepeatingWord(user, message);
                }
            }
            case ("learned") -> {
                String[] texts = text.split(" - ");
                user.fromLeaningToRepeat(texts[0].trim());
                editKeyboardAfterLeanedOrForgot(callbackQuery);
            }
            case ("forgot") -> {
                String[] texts = text.split(" - ");
                user.fromRepeatToLeaning(texts[0].trim());
                editKeyboardAfterLeanedOrForgot(callbackQuery);
            }
        }
    }

    /*Оброботка текстовых команд или в случае, если пользователь присылает слова на добавление в словарь*/
    private void handleTextMessage(Message message) {
        String messageText = message.getText();
        User user = telegramBot.Main.userMap.get(message.getChatId());

        switch (messageText) {
            case ("/start") -> {
                sendMessage(message, "Добро пожаловать в наш бот по изучению английских слов.");
            }
            case ("\uD83D\uDDD2 Добавить слова") -> {
                user.setMenu("inAddMenu");
                sendMessage(message, """
                        Можете отправлять слова, которые хотите добавить в свою коллекию.\s

                        Если нужно добавить несколько слов, можете отправлять их по очереди.

                        Можете отправлять также словосочетания

                        Учтите, что слова переводятся автоматически, с помощью сервисов онлайн перевода и никак не проходят дополнительные проверки орфографии. Поэтому даже при небольших ошибках, перевод также будет ошибочный.""");
            }
            case ("\uD83D\uDDC3 Добавить 50 случайных слов") -> {
                user.add50Words();
                sendMessage(message, "50 случайных слов успешно добавлены в ваш словарь");
            }
            case ("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова") -> {
                user.setMenu("inLeaningMenu");
                getLeaningWord(user, message);
            }
            case ("\uD83D\uDD01 Повторять слова") -> {
                user.setMenu("inRepeatMenu");
                getRepeatingWord(user, message);
            }
            case ("\uD83D\uDCC8 Статистика") -> {
                sendMessage(message, user.getStatistic());
            }
            default -> {
                if (user.isInAddMenu()) {
                    sendMessage(message, user.add(messageText), true);
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

        if (telegramBot.Main.userMap.get(message.getChatId()).inRepeatingProcessContainsKey(texts[0].toLowerCase())) {
            InlineKeyboardButton forgot = new InlineKeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Снова изучать это слово");
            forgot.setCallbackData("forgot");
            keyboard.getKeyboard().get(0).set(1, forgot);
        } else if (telegramBot.Main.userMap.get(message.getChatId()).inLeaningProcessContainsKey(texts[0].toLowerCase())) {
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

    private void getRepeatingWord(User user, Message message) {
        try {
            String wordsForSend = user.getRandomLearningWord();
            sendWordWithVoice(wordsForSend, message);
        } catch (ArrayIndexOutOfBoundsException e) {
            sendMessage(message, "У вас нет слов на повторении в данный момент. Пожалуйста, " +
                    "воспользуйтесь меню \"\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова\"");
        } catch (User.IncorrectMenuSelectionException e) {
            sendMessage(message, "Вы не выбрали меню. Пожалуйста выбери действие которе " +
                    "необходимо выполнить из списка ниже ⬇");
        }
    }

    private void getLeaningWord(User user, Message message) {
        try {
            String wordsForSend = user.getRandomLearningWord();
            sendWordWithVoice(wordsForSend, message);
        } catch (ArrayIndexOutOfBoundsException e) {
            sendMessage(message, "У вас нет слов для изучения в данный момент. Пожалуйста, " +
                    "добавьте новые слова, или воспользуйтесь нашим банком слов.");
        } catch (User.IncorrectMenuSelectionException e) {
            sendMessage(message, "Вы не выбрали меню. Пожалуйста выбери действие которе " +
                    "необходимо выполнить из списка ниже ⬇");
        }
    }

    /*Данный метод отправляет пользователю слово c произношением. В случае невозможность получить аудио файл с произношением
    отправляет просто слово*/
    private void sendWordWithVoice(String key, Message message) {
        User user = telegramBot.Main.userMap.get(message.getChatId());
        Word word;
        if (user.isInLeaningMenu()) {
            word = user.getInLearningProcess(key);
        } else {
            word = user.getInRepeatingProcess(key);
        }

        String textForMessage;

        if (word.getEnWord().equalsIgnoreCase(key)) {
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


    private void statistics(Message message) {

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

        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDDD2 Добавить слова"));
        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDDC3 Добавить 50 случайных слов"));
        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDCC8 Статистика"));
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDD01 Повторять слова"));
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова"));

        keyboardRowList.add(keyboardFirstRow);
        keyboardRowList.add(keyboardSecondRow);

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
        return "SturviTestBot";
    }

    @Override
    public String getBotToken() {
        return "5857743410:AAHyinYvlTc-grG76012Nqj6Of5SGNgmMvE";
    }
}