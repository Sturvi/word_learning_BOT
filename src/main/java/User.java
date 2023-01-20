import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private static final String API_KEY = "AIzaSyAbzEWfx3-YaA4NstSglQztTzpSGSDkmgA";

    private final Map<String, Word> inLeaningProcess;

    private Map<String, Word> inRepeatingProcess;
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

    public void remove(String keyWord) {
        inRepeatingProcess.remove(keyWord);
        inLeaningProcess.remove(keyWord);
    }

    public void fromLeaningToRepeat(String key) {
        inRepeatingProcess.put(key, inLeaningProcess.get(key));
        inLeaningProcess.remove(key);
    }

    public void fromRepeatToLeaning(String key) {
        inLeaningProcess.put(key, inRepeatingProcess.get(key));
        inRepeatingProcess.remove(key);
    }

    public String add(String word) {
        if (word.length() > 1 || word.equalsIgnoreCase("i")) {
            if (!checkInUserMaps(word)) {
                if (AllWordBase.check(word)) {
                    addWordFromAllWordMap(word);
                } else {
                    addWordFromTranslator(word);
                }
                return "Слово (или словосочетание) успешно добавлено в твой словарь";
            } else {
                //Отправить сообщение, что слово уже есть в твоем словаре
                return  "Данное слово находится в вашем словаре";
            }
        } else {
            return  "Слово должно состоять из 2 и более букв";
        }
    }

    private boolean checkInUserMaps(String tempWord) {
        return inLeaningProcess.containsKey(tempWord)
                || inRepeatingProcess.containsKey(tempWord);
    }

    public String getRandomLearningWord() throws ArrayIndexOutOfBoundsException, IncorrectMenuSelectionException {
        if (inLeaningMenu) {
            String[] keysArr = inLeaningProcess.keySet().toArray(new String[0]);
            if (keysArr.length == 0) {
                throw new ArrayIndexOutOfBoundsException();
            }
            int random = (int) (Math.random() * keysArr.length - 1);

            return keysArr[random];
        } else if (inRepeatMenu) {
            String[] keysArr = inRepeatingProcess.keySet().toArray(new String[0]);
            if (keysArr.length == 0) {
                throw new ArrayIndexOutOfBoundsException();
            }
            int random = (int) (Math.random() * keysArr.length - 1);

            return keysArr[random];
        } else {
            throw new IncorrectMenuSelectionException();
        }
    }

    private void addWordFromTranslator(String inputWord) {
        //the first word in this List is in English, the second in Russian
        var translatedWord = Word.translate(inputWord);

        var word = new Word(translatedWord.get(0), translatedWord.get(1));

        if (!translatedWord.get(0).equals(translatedWord.get(1))) {
            inLeaningProcess.put(word.getEnWord(), word);
            inLeaningProcess.put(word.getRuWord(), word);

            AllWordBase.add(word);
        }

        //Нужно добавить отправку сообщения уведомления
    }

    private void addWordFromAllWordMap(String key) {
        List<Word> wordList = AllWordBase.getWordObjects(key);
        for (Word tempWord : wordList) {
            inLeaningProcess.put(tempWord.getEnWord(), tempWord);
            inLeaningProcess.put(tempWord.getRuWord(), tempWord);

        }

        //Нужно добавить отправку сообщения уведомления
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
        return inLeaningProcess.get(key);
    }

    public Word getInRepeatingProcess(String key) {
        return inRepeatingProcess.get(key);
    }

    public boolean isInLeaningMenu() {
        return inLeaningMenu;
    }

    public boolean isInRepeatMenu() {
        return inRepeatMenu;
    }

public static class IncorrectMenuSelectionException extends Exception {
}
}
