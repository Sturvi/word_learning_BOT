package admin;

import telegramBot.Word;

import java.util.LinkedList;
import java.util.Queue;
;

public class AdminsData {
    private static final Queue<Word> wordsQueueForAddingToBase = new LinkedList<>();


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
}
