package telegramBot;

import org.apache.log4j.Logger;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class);

    /**
     * Точка входа в приложение. Инициализирует и запускает чат-бот.
     *
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        LOGGER.info("Запуск программы");

        TelegramBotsApi telegramBotsApi;
        try {
            telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new TelegramApiConnect());
        } catch (TelegramApiException e) {
            LOGGER.error("КОРОЧЕ ПИСЕЦ БОТУ! :-) " + e);
            throw new RuntimeException(e);
        }
    }
}