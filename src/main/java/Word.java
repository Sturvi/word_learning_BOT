import java.util.Objects;

public class Word {

    private final String enWord;
    private final String ruWord;
    private Integer count;

    public Word(String enWord, String ruWord) {
        this.enWord = enWord;
        this.ruWord = ruWord;
        count = 0;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getEnWord() {
        return enWord;
    }

    public String getRuWord() {
        return ruWord;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Word word = (Word) o;
        return enWord.equals(word.enWord) && ruWord.equals(word.ruWord);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enWord, ruWord);
    }
}
