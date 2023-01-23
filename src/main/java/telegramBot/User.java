package telegramBot;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User  implements Serializable {
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

    public String add(String word) {
        if (word.length() > 1 || word.equalsIgnoreCase("i")) {
            if (!checkInUserMaps(word.toLowerCase())) {
                if (AllWordBase.check(word.toLowerCase())) {
                    addWordFromAllWordMap(word);
                } else {
                    addWordFromTranslator(word);
                }
                return "Слово (или словосочетание) успешно добавлено в твой словарь";
            } else {
                //Отправить сообщение, что слово уже есть в твоем словаре
                return "Данное слово находится в вашем словаре";
            }
        } else {
            return "Слово должно состоять из 2 и более букв";
        }
    }

    public void add50Words() {
        String[] wordKeys = AllWordBase.getKeySet();


        for (int count = 0; count < 50; count++) {
            while (true) {
                int randomWord = (int) (Math.random() * wordKeys.length);
                if (wordKeys[randomWord] != null && !checkInUserMaps(wordKeys[randomWord].toLowerCase())) {
                    addWordFromAllWordMap(wordKeys[randomWord].toLowerCase());
                    wordKeys[randomWord] = null;
                    break;
                }
            }
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

    private void addWordFromTranslator(String inputWord) {
        //the first word in this List is in English, the second in Russian
        var translatedWord = Word.translate(inputWord);

        var word = new Word(translatedWord.get(0), translatedWord.get(1));

        if (!translatedWord.get(0).equals(translatedWord.get(1))) {
            inLeaningProcess.put(word.getEnWord().toLowerCase(), word);
            inLeaningProcess.put(word.getRuWord().toLowerCase(), word);

            admin.AdminsData.addWord(word);
        }

        //Нужно добавить отправку сообщения уведомления
    }

    private void addWordFromAllWordMap(String key) {
        List<Word> wordList = AllWordBase.getWordObjects(key.toLowerCase());
        for (Word tempWord : wordList) {
            inLeaningProcess.put(tempWord.getEnWord().toLowerCase(), tempWord);
            inLeaningProcess.put(tempWord.getRuWord().toLowerCase(), tempWord);
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

    public String getStatistic (){
        return "Внимание! Стасистика включает в себя также и дубликаты слов. Например \"Автомобиль -> Car\" и " +
                "\"Car -> Автомобиль\" включены в данный список как два отдельных слова. \n\n" +
                "Изученные слова: " + inRepeatingProcess.keySet().size() + "\n" +
                "Слова на изучении: " + inLeaningProcess.keySet().size();
    }

    public static class IncorrectMenuSelectionException extends Exception {
    }
}
