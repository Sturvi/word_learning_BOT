import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private Map<String, Word> inLearningProcessEngRu;
    private Map<String, Word> inLearningProcessRuEng;
    private Map<String, Word> alreadyLearnedEngRu;
    private Map<String, Word> alreadyLearnedRuEng;
    private AllWordBase allWordBase;


    public User(AllWordBase allWordBase) {
        inLearningProcessEngRu = new HashMap<>();
        inLearningProcessRuEng = new HashMap<>();
        alreadyLearnedEngRu = new HashMap<>();
        alreadyLearnedRuEng = new HashMap<>();
        this.allWordBase = allWordBase;
    }

    public void add(String words) {
        String[] wordsArr = words.trim().split(" ");
        for (String tempWord : wordsArr) {
            if (tempWord.length() > 1) {
                if (allWordBase.check(tempWord)) {
                    addWordFromAllWordMap(tempWord);
                } else {
                    addWordFromTranslator(tempWord);
                }
            }
        }
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

        inLearningProcessEngRu.put(word.getEnWord(), word);
        inLearningProcessRuEng.put(word.getRuWord(), word);

        allWordBase.add(word);

        //Нужно добавить отправку сообщения уведомления
    }

    private void addWordFromAllWordMap(String key) {
        List<Word> wordList = allWordBase.getWordObjects(key);
        for (Word tempWord : wordList) {
            inLearningProcessEngRu.put(tempWord.getEnWord(), tempWord);
            inLearningProcessRuEng.put(tempWord.getRuWord(), tempWord);
        }

        //Нужно добавить отправку сообщения уведомления
    }
}
