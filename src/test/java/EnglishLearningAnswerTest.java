import org.junit.Test;
import telegramBot.Api;

import java.io.IOException;

import static org.junit.Assert.*;

public class EnglishLearningAnswerTest {

    @Test
    public void testGetEnglishLearningAnswer() {
        String question1 = "В чем разница между словами complex и difficult?";

        // Тестирование вопроса, связанного с изучением английского языка
        try {
            String result1 = Api.getEnglishLearningAnswer(question1);

            // Вывод вопроса и ответа на консоль
            System.out.println("Вопрос 1: " + question1);
            System.out.println("Ответ 1: " + result1);

            assertNotNull(result1);
        } catch (IOException e) {
            fail("Тест 1 завершился с исключением: " + e.getMessage());
        }
    }

    @Test
    public void testGetNonEnglishLearningAnswer() {
        String question2 = "Какая формула для расчета площади круга?";

        // Тестирование вопроса, не связанного с изучением языков
        try {
            String result2 = Api.getEnglishLearningAnswer(question2);

            // Вывод вопроса и ответа на консоль
            System.out.println("Вопрос 2: " + question2);
            System.out.println("Ответ 2: " + result2);

            // Проверка наличия ключевых слов, связанных с изучением английского языка
            assertFalse(result2.toLowerCase().contains("английск"));
        } catch (IOException e) {
            fail("Тест 2 завершился с исключением: " + e.getMessage());
        }
    }



}