import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Objects;

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

        File voice = new File("voice/" + getEnWord() + ".mp3");

        if (!voice.exists()) {
            createSpeech(getEnWord(), voice);
        }

        return voice;
    }

    private void createSpeech(String text, File file) throws Exception {
// Replace with your own subscription key and region
        String subscriptionKey = "e2c7953181e04a5cb85981e5a309d7f4";
        String region = "germanywestcentral";

        // Create an configuration object with the subscription key and region
        SpeechConfig config = SpeechConfig.fromSubscription(subscriptionKey, region);

        // Create a synthesizer with the configuration object
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(config);

        // Create a stream to save the TTS output
        OutputStream outputStream;
        try {
            outputStream = new FileOutputStream(file);
        } catch (FileNotFoundException ex) {
            System.out.println("Error creating the output stream: " + ex.getMessage());
            return;
        }

        // Set the synthesizer output format to MP3
        synthesizer.setOutputFormat(SpeechOutputFormat.MP3);

        // Start synthesizing the text
        SpeechSynthesisResult result = synthesizer.speakText(text, outputStream);

        // Check for errors
        if (result.getReason() != ResultReason.SynthesizingAudioCompleted) {
            System.out.println("Error synthesizing the text: " + result.getReason());
            return;
        }

        System.out.println("TTS output saved to output.mp3");
    }

    /*    private void createSpeech(String text, File file) throws Exception {
        TextToSpeechClient ttsClient = TextToSpeechClient.create();

        SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode("en-US")
                .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                .build();
        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3)
                .build();
        SynthesizeSpeechRequest request = SynthesizeSpeechRequest.newBuilder()
                .setInput(input)
                .setVoice(voice)
                .setAudioConfig(audioConfig)
                .build();

        SynthesizeSpeechResponse response = ttsClient.synthesizeSpeech(request);
        ByteString audioContents = response.getAudioContent();

        InputStream inputStream = new ByteArrayInputStream(audioContents.toByteArray());
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
        }

    }*/

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
