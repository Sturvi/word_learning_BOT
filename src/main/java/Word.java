import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import java.io.*;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Scanner;

public class Word {

    private final String enWord;
    private final String ruWord;
    private Integer count;

    private static final String API_KEY = "AIzaSyAbzEWfx3-YaA4NstSglQztTzpSGSDkmgA";

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

    public File getVoice() throws Exception {
        File directory = new File("voice");

        if (!directory.isDirectory()) {
            directory.mkdirs();
        }

        File voice = new File("voice/" + getEnWord() + ".wav");

        if (!voice.exists()) {
            createSpeech(getEnWord(), voice);
        }

        return voice;
    }

    private void createSpeech(String text, File voice) throws Exception {
        // Replace with your own subscription key and region
        String subscriptionKey = "e2c7953181e04a5cb85981e5a309d7f4";
        String serviceRegion = "germanywestcentral";

        // Replace with the path to where you want to save the .wav file
        String filePath = "voice/" + text + ".wav";


        SpeechConfig speechConfig = SpeechConfig.fromSubscription(subscriptionKey, serviceRegion);

        speechConfig.setSpeechSynthesisVoiceName("en-US-JennyNeural");

        SpeechSynthesizer speechSynthesizer = new SpeechSynthesizer(speechConfig);

        if (text.isEmpty()) {
            return;
        }

        // Get the synthesized speech as an audio stream
        SpeechSynthesisResult result = speechSynthesizer.SpeakText(text);

        if (result.getAudioData() != null) {
            try (FileOutputStream fos = new FileOutputStream(voice)) {
                fos.write(result.getAudioData());
                System.out.println("Audio data written to file: output.wav");
            } catch (IOException ex) {
                System.out.println("Error writing audio data to file: " + ex.getMessage());
            }
        } else {
            System.out.println("Error getting audio data: ");
        }

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
