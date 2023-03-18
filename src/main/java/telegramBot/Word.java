package telegramBot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.cognitiveservices.speech.*;
import okhttp3.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Objects;

public class Word implements Serializable {

    private final String enWord;
    private final String ruWord;

    public Word(String enWord, String ruWord) {
        this.enWord = enWord;
        this.ruWord = ruWord;
    }

    public String getEnWord() {
        return enWord;
    }

    public String getRuWord() {
        return ruWord;
    }

    /*Данный метод при запросе возвращает объект File по адресу которого находится аудио файл с английской озвучкой слова.
     * В первую очередь проверяет среди уже сохраненных слов. Если не найдено отправляет в TTS*/
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

    /*Данный метод принимает String который нужно озвучить и File по адресу которого должен находится айдиофайл с
     * извучкой. Посылает данный текст в Microsoft TTS и полученный результат сохраняет по адресу в объекте File*/
    private void createSpeech(String text, File voice) throws Exception {
        // Replace with your own subscription key and region
        String subscriptionKey = "e2c7953181e04a5cb85981e5a309d7f4";
        String serviceRegion = "germanywestcentral";

        try{

            SpeechConfig speechConfig = SpeechConfig.fromSubscription(subscriptionKey, serviceRegion);

            speechConfig.setSpeechSynthesisVoiceName("en-US-JennyNeural");

            SpeechSynthesizer speechSynthesizer = new SpeechSynthesizer(speechConfig);

            if (text.isEmpty()) {
                return;
            }

            // Get the synthesized speech as an audio stream
            SpeechSynthesisResult result = speechSynthesizer.SpeakText(text);

            System.out.println("text :"+text);
            System.out.println("result.id :"+result.getResultId()+", result.resultReason : "+result.getReason()
                    + ", result.audioDuration : "+result.getAudioDuration()+", result.audioLength : "+result.getAudioLength());

            if (result.getAudioData() != null) {
                FileOutputStream fos = new FileOutputStream(voice);
                fos.write(result.getAudioData());
                System.out.println("Audio data written to file: output.wav");

            } else {
                System.out.println("Error getting audio data: ");
            }
        }catch(Exception e){
            e.printStackTrace();
            throw e;
        }
    }

    /*Данный метод принимает String отправляет его в Microsoft TranslatorAPi получает перевод
    и возвращает его в виде ArrayList, где первый элемент это слово на английском, второй элемент на русском*/
    public static ArrayList<String> translate(String word) {
        String key = "9a7b89f2526247049ab6ec3980ae56a8";
        String location = "germanywestcentral";
        OkHttpClient client = new OkHttpClient();

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType,
                "[{\"Text\": \"" + word + "\"}]");
        Request request = new Request.Builder()
                .url("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=en&to=ru")
                .post(body)
                .addHeader("Ocp-Apim-Subscription-Key", key)
                // location required if you're using a multi-service or regional (not global) resource.
                .addHeader("Ocp-Apim-Subscription-Region", location)
                .addHeader("Content-type", "application/json")
                .build();
        Response response;
        String jsonString;

        try {
            response = client.newCall(request).execute();
            jsonString = response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ArrayList<String> result = new ArrayList<>();

        JsonParser jsonParser = new JsonParser();
        JsonArray jsonArray = jsonParser.parse(jsonString).getAsJsonArray();
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();

        JsonArray translations = jsonObject.get("translations").getAsJsonArray();
        for (JsonElement translation : translations) {
            JsonObject translationObject = translation.getAsJsonObject();
            result.add(translationObject.get("text").getAsString());
        }

        return result;
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
