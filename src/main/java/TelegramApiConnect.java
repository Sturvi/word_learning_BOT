import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
import java.util.Map;

/*Данный класс подключается к телеграмм боту, принимает обновления,
распледеляет задачи и отправляет сообщения пользователям*/
public class TelegramApiConnect extends TelegramLongPollingBot {

    @Override
    public void onUpdateReceived(Update update) {
        Long chatId = update.getMessage() == null ? update.getCallbackQuery().getMessage().getChatId() : update.getMessage().getChatId();
        if (!Main.userMap.containsKey(chatId)) {
            Main.userMap.put(chatId, new User());
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
        User user = Main.userMap.get(message.getChatId());
        String data = callbackQuery.getData();
        String text = callbackQuery.getMessage().getText();


        switch (data) {
            case ("delete") -> {
                String[] texts = text.split(" - ");
                user.remove(texts[0].trim());
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
            }
            case ("forgot") -> {
                String[] texts = text.split(" - ");
                user.fromRepeatToLeaning(texts[0].trim());
            }
        }
    }

    /*Оброботка текстовых команд или в случае, если пользователь присылает слова на добавление в словарь*/
    private void handleTextMessage(Message message) {
        String messageText = message.getText();
        User user = Main.userMap.get(message.getChatId());

        switch (messageText) {
            case ("/start") -> {
                sendMessage(message, "Добро пожаловать в наш бот по изучению английских слов.");
                help(message);
            }
            case ("\uD83D\uDDD2 Добавить слова") -> {
                user.setMenu("inAddMenu");
                sendMessage(message, "Можете отправлять слова, которые хотите добавить в свою коллекию " +
                        "\n\n Можете отправлять также словосочетания" +
                        "\n\nУчтите, что слова переводятся автоматически, с помощью сервисов онлайн перевода и " +
                        "никак не проходят дополнительные провекрки орфографии. Поэтому даже при небольших ошибка, " +
                        "перевод также будет ошибочный.");
            }
            case ("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова") -> {
                user.setMenu("inLeaningMenu");
                getLeaningWord(user, message);
            }
            case ("\uD83D\uDD01 Повторять слова") -> {
                user.setMenu("inRepeatMenu");
                getRepeatingWord(user, message);
            }
            default -> {
                if (user.isInAddMenu()) {
                    for (String textForSend : user.add(messageText)) {
                        sendMessage(message, textForSend, true);
                    }
                }
            }
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
            sendMessage(message, "У вас нет слов на изучения в данный момент. Пожалуйста, " +
                    "добавьте новые слова, или воспользуйтесь нашим банком слов.");
        } catch (User.IncorrectMenuSelectionException e) {
            sendMessage(message, "Вы не выбрали меню. Пожалуйста выбери действие которе " +
                    "необходимо выполнить из списка ниже ⬇");
        }
    }

    /*Данный метод отправляет пользователю слово c произношением. В случае невозможность получить аудио файл с произношением
    отправляет просто слово*/
    private void sendWordWithVoice(String key, Message message) {
        User user = Main.userMap.get(message.getChatId());
        Word word;
        if (user.isInLeaningMenu()){
           word  = user.getInLearningProcess(key);
        } else {
            word  = user.getInRepeatingProcess(key);
        }

        String textForMessage;

        if (word.getEnWord().equals(key)) {
            textForMessage = key + " -  <span class='tg-spoiler'>   " + word.getRuWord() + "   </span>";
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


    private void help(Message message) {

    }

    /*Отправка обычных тектовых сообщений. Принимает Long chatId и текст сообщения*/
    public void sendMessage(Message message, String text) {
        sendMessage(message, text, false);
    }

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

    private void sendMsg(SendMessage sendMessage) {
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
        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDEE0 Помощь"));
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDD01 Повторять слова"));
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова"));

        keyboardRowList.add(keyboardFirstRow);
        keyboardRowList.add(keyboardSecondRow);

        replyKeyboardMarkup.setKeyboard(keyboardRowList);
    }

    private InlineKeyboardMarkup getKeyboard(Long chatId) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());
        keyboard.add(new ArrayList<>());

        InlineKeyboardButton delete = new InlineKeyboardButton("❌ Удалить это слово");
        delete.setCallbackData("delete");
        keyboard.get(0).add(delete);

        if (Main.userMap.get(chatId).isInLeaningMenu()) {
            InlineKeyboardButton learned = new InlineKeyboardButton("\uD83E\uDDE0 Уже знаю это слово");
            learned.setCallbackData("learned");
            keyboard.get(0).add(learned);
        } else if (Main.userMap.get(chatId).isInRepeatMenu()) {
            InlineKeyboardButton forgot = new InlineKeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Снова изучать это слово");
            forgot.setCallbackData("forgot");
            keyboard.get(0).add(forgot);
        }

        InlineKeyboardButton next = new InlineKeyboardButton("➡ Слудеющее слово");
        next.setCallbackData("next");
        keyboard.get(1).add(next);

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        return keyboardMarkup;
    }

    @Override
    public String getBotUsername() {
        return "WordLeaningBot";
    }

    @Override
    public String getBotToken() {
        return "5915434126:AAHto2nUM8S1a9cb2Fgxz8F3P45BV4QGp7U";
    }
}