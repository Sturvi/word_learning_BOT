package telegramBot;

import admin.Admin;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AllWordBase {
    private static Map<String, ArrayList<Word>> allWordInBase = new HashMap<>();

    public static ArrayList<Word> getWordObjects(String key) {
        return allWordInBase.get(key.toLowerCase());
    }

    public static boolean check(String key) {
        return allWordInBase.containsKey(key.toLowerCase());
    }

    public static void add(Word word) {

        if (!allWordInBase.containsKey(word.getEnWord())) {
            allWordInBase.put(word.getEnWord().toLowerCase(), new ArrayList<>());
        }
        if (!allWordInBase.containsKey(word.getRuWord())) {
            allWordInBase.put(word.getRuWord().toLowerCase(), new ArrayList<>());
        }

        allWordInBase.get(word.getEnWord().toLowerCase()).add(word);
        allWordInBase.get(word.getRuWord().toLowerCase()).add(word);
    }

    public static int getWordBaseSize() {
        return allWordInBase.keySet().size();
    }

    public static String[] getKeySet(){
        return allWordInBase.keySet().toArray(new String[0]);
    }

    public static void backupAllWord (){
        try (FileOutputStream fos = new FileOutputStream("backupDir/AllWordBase.txt");
             ObjectOutputStream oos = new ObjectOutputStream(fos)){
            oos.writeObject(allWordInBase);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void restoreAllWord (){
        try (FileInputStream fis = new FileInputStream("backupDir/AllWordBase.txt");
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            allWordInBase = (Map<String, ArrayList<Word>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
