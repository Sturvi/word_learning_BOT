import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private static final String API_KEY = "AIzaSyAbzEWfx3-YaA4NstSglQztTzpSGSDkmgA";

    private final Map<String, Word> inLearningProcess;

    private Map<String, Word> alreadyLearned;
    private boolean inAddMenyu;

    public User() {
        inLearningProcess = new HashMap<>();
        alreadyLearned = new HashMap<>();
        inAddMenyu = false;
    }

    public void add(String words) {
        String[] wordsArr = words.trim().split(" ");
        for (String tempWord : wordsArr) {
            if (tempWord.length() > 1) {
                if (!checkInUserMaps(tempWord)) {
                    if (AllWordBase.check(tempWord)) {
                        addWordFromAllWordMap(tempWord);
                    } else {
                        addWordFromTranslator(tempWord);
                    }
                } else {
                    //Отправить сообщение, что слово уже есть в твоем словаре
                }
            } else {
                //отправить сообщение, что слово должно состоять из 2 и более слов
            }
        }
    }

    private boolean checkInUserMaps(String tempWord) {
        return inLearningProcess.containsKey(tempWord)
                || alreadyLearned.containsKey(tempWord);
    }

    public String getRandomLearningWord() {
        String[] keysArr = inLearningProcess.keySet().toArray(new String[0]);
        int random = (int) (Math.random() * keysArr.length - 1);

        return keysArr[random];
    }

    private void addWordFromTranslator(String inputWord) {
        //the first word in this List is in English, the second in Russian
        var translatedWord = Word.translate(inputWord);

        var word = new Word(translatedWord.get(0), translatedWord.get(1));

        if (!translatedWord.get(0).equals(translatedWord.get(1))) {
            inLearningProcess.put(word.getEnWord(), word);
            inLearningProcess.put(word.getRuWord(), word);

            AllWordBase.add(word);
        }

        //Нужно добавить отправку сообщения уведомления
    }

    private void addWordFromAllWordMap(String key) {
        List<Word> wordList = AllWordBase.getWordObjects(key);
        for (Word tempWord : wordList) {
            inLearningProcess.put(tempWord.getEnWord(), tempWord);
            inLearningProcess.put(tempWord.getRuWord(), tempWord);

        }

        //Нужно добавить отправку сообщения уведомления
    }


    public boolean isInAddMenu() {
        return inAddMenyu;
    }

    public void setInAddMenu(boolean inAddMenu) {
        this.inAddMenyu = inAddMenu;
    }

    public Word getInLearningProcess(String key) {
        return inLearningProcess.get(key);
    }
}
