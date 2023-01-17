import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AllWordBase {
    private final Map<String, ArrayList<Word>> allWordInBase;


    public AllWordBase() {
        allWordInBase = new HashMap<>();
    }

    public ArrayList<Word> getWordObjects(String key) {
        return allWordInBase.get(key);
    }

    public boolean check(String key) {
        return allWordInBase.containsKey(key);
    }

    public void add(Word word) {

        if (!allWordInBase.containsKey(word.getEnWord())) {
            allWordInBase.put(word.getEnWord(), new ArrayList<>());
        }
        if (!allWordInBase.containsKey(word.getRuWord())) {
            allWordInBase.put(word.getRuWord(), new ArrayList<>());
        }

        allWordInBase.get(word.getEnWord()).add(word);
        allWordInBase.get(word.getRuWord()).add(word);
    }
}
