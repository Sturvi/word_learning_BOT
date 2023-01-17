import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private final Map<String, Word> inLearningProcess;
    private Map<String, Word> alreadyLearned;
    private final AllWordBase allWordBase;


    public User(AllWordBase allWordBase) {
        inLearningProcess = new HashMap<>();
        alreadyLearned = new HashMap<>();
        this.allWordBase = allWordBase;
    }

    public void add(String words) {
        String[] wordsArr = words.trim().split(" ");
        for (String tempWord : wordsArr) {
            if (tempWord.length() > 1) {
                if (!checkInUserMaps(tempWord)) {
                    if (allWordBase.check(tempWord)) {
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

    private void addWordFromTranslator(String inputWord) {
        ArrayList<String> translatedWord = new ArrayList<>();

        TranslatorText translator = new TranslatorText();
        try {
            //the first word in this List is in English, the second in Russian
            translatedWord = translator.post(inputWord);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Word word = new Word(translatedWord.get(0), translatedWord.get(1));

        inLearningProcess.put(word.getEnWord(), word);
        inLearningProcess.put(word.getRuWord(), word);

        allWordBase.add(word);

        //Нужно добавить отправку сообщения уведомления
    }

    private void addWordFromAllWordMap(String key) {
        List<Word> wordList = allWordBase.getWordObjects(key);
        for (Word tempWord : wordList) {
            inLearningProcess.put(tempWord.getEnWord(), tempWord);
            inLearningProcess.put(tempWord.getRuWord(), tempWord);
        }

        //Нужно добавить отправку сообщения уведомления
    }
}
