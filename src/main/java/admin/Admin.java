package admin;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import telegramBot.TelegramApiConnect;
import telegramBot.Word;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Admin  implements Serializable {
    private final Long chatID;
    private final String userName;

    public Admin(Long chatID, String userName) {
        this.chatID = chatID;
        this.userName = userName;
    }

    public Admin() {
        this.chatID = Long.valueOf("5572592522");
        this.userName = "TheBestBotsInTheWorld";
    }

    public void inputCommand(Update update) {
        if (update.hasCallbackQuery()) {
            handleAdminCallback(update.getCallbackQuery());
        } else {
            handleAdminTextMessage(update.getMessage());
        }
    }

    private void handleAdminTextMessage(Message message) {
        String messageText = message.getText();

        switch (messageText) {
            case ("Проверять слова") -> {
                wordsChecking();
            }
            case ("Получить статистику") -> {
                getStatistic();
            }
            default -> {
                sendTextMessage("Выберите пункт меню! \nСлава КПСС");
            }
        }
    }

    private void handleAdminCallback(CallbackQuery callbackQuery) {
        String command = callbackQuery.getData();
        switch (command) {
            case "delete" -> {
                editKeyboardAfterDecided(callbackQuery);
                AdminsData.removeWord();
                return;
            }
            case "ok" -> {
                String[] textInMessage = callbackQuery.getMessage().getText().trim().replaceAll("Eng: ", "")
                        .replaceAll("Ru: ", "").split("\n");
                //Word word = Word.getRandomWord();
                //AllWordBase.add(word);

                editKeyboardAfterDecided(callbackQuery);
                AdminsData.removeWord();
            }
            case "next" -> wordsChecking();
        }
    }

    private void editKeyboardAfterDecided(CallbackQuery callbackQuery) {
        TelegramApiConnect telegramApiConnect = new TelegramApiConnect();
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(callbackQuery.getMessage().getChatId());
        editMessageReplyMarkup.setMessageId(callbackQuery.getMessage().getMessageId());
        editMessageReplyMarkup.setReplyMarkup(getWordCheckKeyboard(true));

        try {
            telegramApiConnect.execute(editMessageReplyMarkup);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void getStatistic() {
        /*String messageText = "Количество Пользователей: " + Main.userMap.keySet().size() + "\n" +
                "Количество Слов в Базе: " + AllWordBase.getWordBaseSize() / 2 + "\n\n" +
                "Cлов в очереди на проверку: " + AdminsData.queueSize() +  "\n\n" +
                "Слава КПСС";

        sendTextMessage(messageText);*/
    }

    private void wordsChecking() {
        Word word = admin.AdminsData.getWord();
        if (word == null) {
            sendTextMessage("Список слов на проверке пуст! \n\nСлава КПСС");
        } else {
            TelegramApiConnect telegramApiConnect = new TelegramApiConnect();
            SendMessage sendMessage = new SendMessage();
            sendMessage.setText("Eng: " + word.getEnWord() + "\nRu: " + word.getRuWord());
            sendMessage.setChatId(getChatID());
            sendMessage.setReplyMarkup(getWordCheckKeyboard(false));
            telegramApiConnect.sendMsg(sendMessage);
        }
    }

    private void sendTextMessage(String text) {
        TelegramApiConnect telegramApiConnect = new TelegramApiConnect();
        SendMessage sendMessage = new SendMessage();
        sendMessage.setText(text);
        sendMessage.setChatId(getChatID());
        setAdminButtons(sendMessage);
        telegramApiConnect.sendMsg(sendMessage);
    }

    private InlineKeyboardMarkup getWordCheckKeyboard(boolean decided) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(new ArrayList<>());

        if (!decided) {
            InlineKeyboardButton delete = new InlineKeyboardButton("❌");
            delete.setCallbackData("delete");
            keyboard.get(0).add(delete);
            InlineKeyboardButton ok = new InlineKeyboardButton("✅");
            ok.setCallbackData("ok");
            keyboard.get(0).add(ok);
        } else {
            InlineKeyboardButton decidedButton = new InlineKeyboardButton("\uD83D\uDDC3 Приговор вынесен");
            decidedButton.setCallbackData("decidedButton");
            keyboard.get(0).add(decidedButton);

            keyboard.add(new ArrayList<>());
            InlineKeyboardButton next = new InlineKeyboardButton("➡️Следующее слово");
            next.setCallbackData("next");
            keyboard.get(1).add(next);

        }

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);

        return keyboardMarkup;
    }

    public void setAdminButtons(SendMessage sendMessage) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboardRowList = new ArrayList<>();
        KeyboardRow keyboardFirstRow = new KeyboardRow();
        KeyboardRow keyboardSecondRow = new KeyboardRow();

        keyboardFirstRow.add(new KeyboardButton("Получить статистику"));
        keyboardSecondRow.add(new KeyboardButton("Проверять слова"));

        keyboardRowList.add(keyboardFirstRow);
        keyboardRowList.add(keyboardSecondRow);

        replyKeyboardMarkup.setKeyboard(keyboardRowList);
    }

    public Long getChatID() {
        return chatID;
    }

    public String getUserName() {
        return userName;
    }


}
