package admin;

import telegramBot.Word;

import java.io.*;
import java.util.LinkedList;
import java.util.Queue;
;

public class AdminsData {
    private static Queue<Word> wordsQueueForAddingToBase = new LinkedList<>();


    public static void addWord (Word word){
        wordsQueueForAddingToBase.add(word);
    }

    static Word getWord (){
        return wordsQueueForAddingToBase.peek();
    }

    static Word removeWord (){
        return wordsQueueForAddingToBase.remove();
    }

    static int queueSize (){
        return wordsQueueForAddingToBase.size();
    }

    public static void restoreWordsQueueForAddingToBase() {
        try (FileInputStream fis = new FileInputStream("backupDir/wordsQueueForAddingToBase.txt");
             ObjectInputStream ois = new ObjectInputStream(fis)) {
             wordsQueueForAddingToBase = (Queue<Word>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void backupUserMapAndAdmin() {
        try (FileOutputStream fos = new FileOutputStream("backupDir/wordsQueueForAddingToBase.txt");
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(wordsQueueForAddingToBase);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
