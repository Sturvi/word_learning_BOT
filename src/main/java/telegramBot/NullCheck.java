package telegramBot;

import org.apache.log4j.Logger;

@FunctionalInterface
public interface NullCheck {
    Logger getLogger();

    default void checkForNull(String methodName, Object... objects) {
        for (Object obj : objects) {
            if (obj == null) {
                getLogger().error("Входные данные в методе " + methodName + " содержит null ");
            }
        }
    }
}