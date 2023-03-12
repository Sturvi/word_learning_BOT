package telegramBot.user;

import dataBase.DatabaseConnection;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.meta.api.objects.Message;
import telegramBot.AllWordBase;
import telegramBot.TelegramApiConnect;
import telegramBot.Word;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class User implements Serializable {
    private final Map<String, Word> inLeaningProcess;
    private final Map<String, Word> inRepeatingProcess;
    private boolean inAddMenu;
    private boolean inLeaningMenu;
    private boolean inRepeatMenu;

    public User() {
        inLeaningProcess = new HashMap<>();
        inRepeatingProcess = new HashMap<>();
        inAddMenu = false;
        inLeaningMenu = false;
        inRepeatMenu = false;
    }

    public void removeWord(String keyWord) {
        inRepeatingProcess.remove(keyWord.toLowerCase());
        inLeaningProcess.remove(keyWord.toLowerCase());
    }

    public void fromLeaningToRepeat(String key) {
        inRepeatingProcess.put(key.toLowerCase(), inLeaningProcess.get(key.toLowerCase()));
        inLeaningProcess.remove(key.toLowerCase());
    }

    public void fromRepeatToLeaning(String key) {
        inLeaningProcess.put(key.toLowerCase(), inRepeatingProcess.get(key.toLowerCase()));
        inRepeatingProcess.remove(key.toLowerCase());
    }

    public boolean inLeaningProcessContainsKey(String key) {
        return inLeaningProcess.containsKey(key.toLowerCase());
    }

    public boolean inRepeatingProcessContainsKey(String key) {
        return inRepeatingProcess.containsKey(key.toLowerCase());
    }

    public String add(@NotNull String word, Long userId) {
        if (word.length() > 1 || word.equalsIgnoreCase("i")) {
            Set<Integer> wordId = new HashSet<>();
            WordsInDatabase.checkNewWordInDB(word, wordId);

            if (wordId.size() == 0) {
                WordsInDatabase.addNewWordToDBFromTranslator(word, wordId);
            }

            WordsInDatabase.checkWordInUserDictionary(wordId, userId);

            if (wordId.isEmpty()){
                return "Данное слово (или словосочетание) уже находятся в твоем словаре";
            }

            WordsInDatabase.addNewWordsToUserDictionary(wordId, userId);
            return "Слово (или словосочетание) успешно добавлено в твой словарь";
        } else {
            return "Слово должно состоять из 2 и более букв";
        }
    }

    private boolean checkInUserMaps(String tempWord) {
        return inLeaningProcess.containsKey(tempWord.toLowerCase())
                || inRepeatingProcess.containsKey(tempWord.toLowerCase());
    }

    public String getRandomLearningWord() throws ArrayIndexOutOfBoundsException, IncorrectMenuSelectionException {
        if (inLeaningMenu) {
            String[] keysArr = inLeaningProcess.keySet().toArray(new String[0]);
            if (keysArr.length == 0) {
                throw new ArrayIndexOutOfBoundsException();
            }
            int random = (int) (Math.random() * keysArr.length);

            return keysArr[random];
        } else if (inRepeatMenu) {
            String[] keysArr = inRepeatingProcess.keySet().toArray(new String[0]);
            if (keysArr.length == 0) {
                throw new ArrayIndexOutOfBoundsException();
            }
            int random = (int) (Math.random() * keysArr.length);

            return keysArr[random];
        } else {
            throw new IncorrectMenuSelectionException();
        }
    }

    public boolean isInAddMenu() {
        return inAddMenu;
    }

    public void setMenu(String menu) {
        switch (menu) {
            case ("inAddMenu"):
                inAddMenu = true;
                inRepeatMenu = false;
                inLeaningMenu = false;
                break;
            case ("inRepeatMenu"):
                inAddMenu = false;
                inRepeatMenu = true;
                inLeaningMenu = false;
                break;
            case ("inLeaningMenu"):
                inAddMenu = false;
                inRepeatMenu = false;
                inLeaningMenu = true;
                break;
            default:
                inAddMenu = false;
                inRepeatMenu = false;
                inLeaningMenu = false;
        }
    }

    public Word getInLearningProcess(String key) {
        return inLeaningProcess.get(key.toLowerCase());
    }

    public Word getInRepeatingProcess(String key) {
        return inRepeatingProcess.get(key.toLowerCase());
    }

    public boolean isInLeaningMenu() {
        return inLeaningMenu;
    }

    public boolean isInRepeatMenu() {
        return inRepeatMenu;
    }

    public String getStatistic() {
        return "Внимание! Стасистика включает в себя также и дубликаты слов. Например \"Автомобиль -> Car\" и " +
                "\"Car -> Автомобиль\" включены в данный список как два отдельных слова. \n\n" +
                "Изученные слова: " + inRepeatingProcess.keySet().size() + "\n" +
                "Слова на изучении: " + inLeaningProcess.keySet().size();
    }

    /* В случаях когда boolean переменная  leaningList true метод возвращает списов изучаемых слов.
    В противном случае, повторяемых*/
    public void getLeaningWordList(Message message, boolean leaningList) {
        StringBuilder allWords = new StringBuilder();
        String[] keys = leaningList ? inLeaningProcess.keySet().toArray(new String[0]) : inRepeatingProcess.keySet().toArray(new String[0]);
        List<Word> usedWords = new ArrayList<>();
        TelegramApiConnect telegramApiConnect = new TelegramApiConnect();

        if (keys.length == 0){
            telegramApiConnect.sendMessage(message, "\uD83D\uDE14  Ваш список пуст");
            return;
        }

        for (String key : keys) {
            Word word = leaningList ? inLeaningProcess.get(key) : inRepeatingProcess.get(key);
            if (!usedWords.contains(word)){
                usedWords.add(word);
                allWords.append(word.getEnWord() + " - " + word.getRuWord() + "\n");
            }
            if (allWords.length() >= 3900){
                telegramApiConnect.sendMessage(message, allWords.toString());
                allWords = new StringBuilder();
            }
        }
        if (allWords.length() > 0) {
            telegramApiConnect.sendMessage(message, allWords.toString());
        }
    }

    public static class IncorrectMenuSelectionException extends Exception {
    }
}
