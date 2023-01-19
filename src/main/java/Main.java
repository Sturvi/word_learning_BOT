import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        /*TranslatorText translatorText = new TranslatorText();
        System.out.println(translatorText.translate("remove"));*/
        Map<Long, User> userMap = new HashMap<>();
        TelegramBotsApi telegramBotsApi = null;
        try {
            telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new TelegramApiConnect(userMap));
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}