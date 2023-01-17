import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TelegramApiConnect extends TelegramLongPollingBot {
    Map<Long, User> userMap;

    public TelegramApiConnect(Map<Long, User> userMap) {
        this.userMap = userMap;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Long chatId = update.getMessage() == null ? update.getCallbackQuery().getMessage().getChatId() : update.getMessage().getChatId();
        if (!userMap.containsKey(chatId)){
            userMap.put(chatId, new User());
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        } else {
            handleTextMessage(update.getMessage());
        }

    }

    private void handleCallback(CallbackQuery callbackQuery) {

    }

    private void handleTextMessage(Message message){
        String messageText = message.getText();
        User user = userMap.get(message.getChatId());

        if (user.isInAddMenu()){
            user.add(messageText);
        } else {
            switch (messageText){
                case ("\uD83D\uDDD2 Добавить слова"):
                    user.setInAddMenu(true);
                    sendMessage(message.getChatId(), "Можете отправлять слова, которые хотите добавить в свою коллекию " +
                            "\n\n Можете отправлять много слов, разделенных пробелами или в разных сообщениях");
                    break;

            }
        }




    }

    private void help (Message message){

    }

    public void sendMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(chatId.toString());
        // sendMessage.setReplyToMessageId(message.getMessageId());
        sendMessage.setText(text);
        try {
            setButtons(sendMessage);
            execute (sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void setButtons (SendMessage sendMessage){
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboardRowList = new ArrayList<>();
        KeyboardRow keyboardFirstRow = new KeyboardRow();
        KeyboardRow keyboardSecondRow = new KeyboardRow();

        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDDD2 Добавить слова"));
        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDDC3 Добавить 100 случайных слов"));
        keyboardFirstRow.add(new KeyboardButton("\uD83D\uDEE0 Помощь"));
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDD01 Повторять слова"));
        keyboardSecondRow.add(new KeyboardButton("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF93 Учить слова"));

        keyboardRowList.add(keyboardFirstRow);
        keyboardRowList.add(keyboardSecondRow);

        replyKeyboardMarkup.setKeyboard(keyboardRowList);
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