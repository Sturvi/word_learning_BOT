package telegramBot;

import dataBase.DatabaseConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.sql.*;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class BotUser {

    private static final Logger LOGGER = Logger.getLogger(BotUser.class);
    private static final NullCheck nullCheck = () -> LOGGER;
    private final Long userId;
    private final Message message;
    private final CallbackQuery callbackQuery;
    private String userMenu;

    private BotUser(Long userID, Message message, CallbackQuery callbackQuery, String userMenu) {
        this.userId = userID;
        this.message = message;
        this.callbackQuery = callbackQuery;
        this.userMenu = userMenu;
    }

    /**
     * Метод getBotUser получает информацию о пользователе из базы данных,
     * используя идентификатор чата, и возвращает объект BotUser.
     *
     * @param update объект обновления, содержащий информацию о пользователе и сообщении
     * @return объект BotUser с информацией о пользователе
     */
    public static BotUser getBotUser(Update update) {
        User user;
        Message message;
        CallbackQuery callbackQuery = null;
        if (update.getMessage() == null) {
            message = update.getCallbackQuery().getMessage();
            callbackQuery = update.getCallbackQuery();
            user = update.getCallbackQuery().getFrom();
        } else {
            message = update.getMessage();
            user = update.getMessage().getFrom();
        }

        checkUser(user);
        String userMenu = getUserMenuFromDatabase(message.getChatId());
        return new BotUser(message.getChatId(), message, callbackQuery, userMenu);
    }

    /**
     * Метод getUserId возвращает идентификатор пользователя.
     *
     * @return идентификатор пользователя (userId)
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Метод getMessage возвращает объект сообщения пользователя.
     *
     * @return объект сообщения (message)
     */
    public Message getMessage() {
        return message;
    }

    /**
     * Метод getCallbackQuery возвращает объект CallbackQuery, содержащий информацию о
     * колбэке (callback) от пользовательского интерфейса, такого как нажатие кнопки.
     *
     * @return объект CallbackQuery, содержащий информацию о колбэке
     */
    public CallbackQuery getCallbackQuery() {
        return callbackQuery;
    }

    /**
     * Метод callbackQueryIsNull проверяет, является ли объект CallbackQuery равным null.
     *
     * @return true, если объект CallbackQuery равен null, иначе false
     */
    public boolean callbackQueryIsNull() {
        return callbackQuery == null;
    }

    /**
     * Метод getUserMenu возвращает меню пользователя.
     * Если меню пользователя равно null, запрашивает его из базы данных.
     *
     * @return меню пользователя или null, если меню не найдено
     */
    public @Nullable String getUserMenu() {
        if (userMenu == null) {
            userMenu = getUserMenuFromDatabase(userId);
        }
        return userMenu;
    }

    /**
     * Получает пользовательское меню из базы данных по идентификатору пользователя.
     *
     * @param userId Идентификатор пользователя, для которого нужно получить меню.
     * @return Строка, содержащая название пользовательского меню или null, если меню не найдено.
     */
    private static @Nullable String getUserMenuFromDatabase(Long userId) {
        // Получение соединения с базой данных
        Connection connection = DatabaseConnection.getConnection();

        if (connection == null) {
            LOGGER.error("getUserMenu Ошибка подключения к БД. connection вернулся null");
            return null;
        }

        // Запрос к базе данных для получения меню пользователя
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT menu_name FROM user_menu WHERE user_id = ?")) {

            preparedStatement.setLong(1, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            // Возвращение названия меню, если оно найдено
            if (resultSet.next()) {
                return resultSet.getString("menu_name");
            }
        } catch (SQLException e) {
            LOGGER.error("Не удалось получить меню из БД" + e);
            throw new RuntimeException(e);
        }

        // Возвращение null, если меню не найдено
        return null;
    }

    /**
     * Устанавливает пользовательское меню для указанного пользователя и сохраняет изменения в базе данных.
     *
     * @param menuName Название пользовательского меню, которое необходимо установить.
     */
    public void setMenu(String menuName) {
        // Проверка на null
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("setMenu", userId, menuName);

        // Установка нового значения пользовательского меню
        userMenu = menuName;

        // Получение соединения с базой данных
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("setMenu connection ", connection);

        // Обновление значения пользовательского меню в базе данных
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE  user_menu SET menu_name = ? WHERE user_id = ?")) {
            ps.setString(1, menuName);
            ps.setLong(2, userId);
            ps.executeUpdate();
            LOGGER.info("Меню для пользователя изменено");
        } catch (SQLException e) {
            LOGGER.error("setMenu ОШИБКА смены меню в БД " + e);
        }
    }

    /**
     * Метод getStatistic получает статистику из базы данных о словах, изучаемых пользователем.
     * Использует NullCheck для проверки на null и PreparedStatement для выполнения запроса к БД.
     * Результаты запроса форматируются в строку и возвращаются.
     *
     * @return Форматированная строка со статистикой пользователя.
     */
    public @NotNull String getStatistic() {
        // Проверка на null
        NullCheck nullCheck = () -> LOGGER;
        nullCheck.checkForNull("getStatistic", userId);

        // Получение соединения с базой данных
        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("getStatistic Connection", connection);

        // Создание объекта StringBuilder для форматирования результата
        StringBuilder stringBuilder = new StringBuilder();

        // Запрос к базе данных для получения статистики
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT " +
                "COUNT(CASE WHEN list_type = 'learning' THEN word_id END) AS learning_count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 1 THEN word_id END) AS repetition_1count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 2 THEN word_id END) AS repetition_2count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 3 THEN word_id END) AS repetition_3count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 4 THEN word_id END) AS repetition_4count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 5 THEN word_id END) AS repetition_5count, " +
                "COUNT(CASE WHEN list_type = 'repetition' AND timer_value = 6 THEN word_id END) AS repetition_6count, " +
                "COUNT(CASE WHEN list_type = 'learned' THEN word_id END) AS learned_count " +
                "FROM user_word_lists WHERE user_id = ?")) {
            preparedStatement.setLong(1, userId);

            ResultSet resultSet = preparedStatement.executeQuery();

            // Форматирование результата
            if (resultSet.next()) {
                stringBuilder.append("Слова на изучении: ").append(resultSet.getInt("learning_count")).append("\n");
                if (resultSet.getInt("repetition_1count") != 0) {
                    stringBuilder.append("Слова на повторении 1 уровня: ").append(resultSet.getInt("repetition_1count")).append("\n");
                }
                if (resultSet.getInt("repetition_2count") != 0) {
                    stringBuilder.append("Слова на повторении 2 уровня: ").append(resultSet.getInt("repetition_2count")).append("\n");
                }
                if (resultSet.getInt("repetition_3count") != 0) {
                    stringBuilder.append("Слова на повторении 3 уровня️: ").append(resultSet.getInt("repetition_3count")).append("\n");
                }
                if (resultSet.getInt("repetition_4count") != 0) {
                    stringBuilder.append("Слова на повторении 4 уровня️: ").append(resultSet.getInt("repetition_4count")).append("\n");
                }
                if (resultSet.getInt("repetition_5count") != 0) {
                    stringBuilder.append("Слова на повторении 5 уровня️: ").append(resultSet.getInt("repetition_5count")).append("\n");
                }
                if (resultSet.getInt("repetition_6count") != 0) {
                    stringBuilder.append("Слова на повторении 6 уровня️: ").append(resultSet.getInt("repetition_6count")).append("\n");
                }
                stringBuilder.append("Изученные слова: ").append(resultSet.getInt("learned_count")).append("\n");
            }
            LOGGER.info("Данные статистики из БД получены");

        } catch (SQLException e) {
            LOGGER.error("ОШИБКА получении данных статистики из БД " + e);
            throw new RuntimeException(e);
        }

        return stringBuilder.toString();
    }

    /**
     * Метод checkUser проверяет и добавляет или обновляет данные пользователя в базе данных.
     * Также добавляет меню пользователя по умолчанию, если он отсутствует.
     *
     * @param user объект User, содержащий информацию о пользователе
     */
    private static void checkUser(User user) {
        // Проверка на null и логирование
        NullCheck nullChecker = () -> LOGGER;
        nullChecker.checkForNull("checkUser", user);

        // Получение соединения с базой данных
        Connection connection = DatabaseConnection.getConnection();

        // Обработка ошибки подключения к БД
        if (connection == null) {
            LOGGER.error("checkUser Ошибка подключения к БД. connection вернулся null");
            return;
        }

        // Извлечение данных пользователя
        long user_id = user.getId();
        String first_name = user.getFirstName();
        String last_name = user.getLastName();
        String username = user.getUserName();
        LocalDateTime localDateTime = LocalDateTime.now(ZoneId.of("UTC"));

        // Добавление или обновление пользователя в БД
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (user_id, first_name, last_name, username, last_contact_time) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT (user_id) " +
                        "DO UPDATE SET first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, " +
                        "username = EXCLUDED.username, last_contact_time = EXCLUDED.last_contact_time")) {
            ps.setLong(1, user_id);
            ps.setString(2, first_name);
            ps.setString(3, last_name);
            ps.setString(4, username);
            ps.setTimestamp(5, Timestamp.valueOf(localDateTime));
            ps.executeUpdate();
            LOGGER.info("Пользователь " + username + " добавлен/обновлен в БД");
        } catch (SQLException e) {
            LOGGER.error("Ошибка добавления/обновления пользователя в БД" + e);
        }

        // Добавление меню по умолчанию для пользователя, если отсутствует
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_menu (user_id, menu_name) " +
                        "VALUES (?, ?) " +
                        "ON CONFLICT DO NOTHING")) {
            ps.setLong(1, user_id);
            ps.setString(2, "NULL");
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Ошибка в добавлении меню по умолчанию в БД " + e);
        }
    }

    /**
     * Обновляет прогресс изучения слова для указанного пользователя, перемещая слово между списками (изучаемые, повторение, выученные)
     * в зависимости от количества повторений.
     *
     * @param word объект слова
     */
    public void updateUserWordProgress(Word word) {
        // Проверка на null значений
        nullCheck.checkForNull("changeWordListType ", word);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("changeWordListType connection ", connection);

        // Получаем количество повторений слова из базы данных
        Integer repetitions = getRepetitionsCount(word);

        // Определяем новый тип списка на основе количества повторений
        String newListType = (repetitions < 6) ? "repetition" : "learned";
        updateUserWordProgressInDB(newListType, word);

        LOGGER.info(MessageFormat.format("Слово переведено в список {0} или обновлено количество повторений", newListType));
    }

    /**
     * Обновляет прогресс изучения слова в базе данных пользователя.
     * В таблице "user_word_lists" обновляются поля "list_type", "timer_value" и "last_repetition_time".
     *
     * @param listType тип списка (изучаемые, повторение, выученные)
     * @param word     объект слова
     */
    private void updateUserWordProgressInDB(String listType, Word word) {
        // Проверка на null значений
        nullCheck.checkForNull("updateUserWordProgressInDB ", listType, word);

        Connection connection = DatabaseConnection.getConnection();
        nullCheck.checkForNull("updateUserWordProgressInDB Connection ", connection);

        // Обновление прогресса слова в базе данных
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "UPDATE user_word_lists " +
                        "SET list_type = ?, timer_value = timer_value + ?, last_repetition_time = now() " +
                        "WHERE user_id = ? AND word_id = ?")) {
            preparedStatement.setString(1, listType);
            preparedStatement.setInt(2, 1);
            preparedStatement.setLong(3, userId);
            preparedStatement.setInt(4, word.getWordId());
            preparedStatement.execute();
            LOGGER.info("Слово успешно переведено в другой словарь пользователя");
        } catch (SQLException e) {
            LOGGER.error("Ошибка смены словаря в БД для пользователя" + e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Метод получает тип списка слов из базы данных по идентификатору пользователя и объекту слова.
     *
     * @param word объект слова
     * @return тип списка слов
     */
    private String getListTypeFromDB(@NotNull Word word) {
        Connection connection = DatabaseConnection.getConnection();
        String list_type = null;

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT list_type FROM user_word_lists WHERE user_id = ? AND word_id = ?")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setInt(2, word.getWordId());
            ResultSet resultSet = preparedStatement.executeQuery();
            nullCheck.checkForNull("editKeyboardAfterLeanedOrForgot resultSet ", resultSet);

            while (resultSet.next()) {
                list_type = resultSet.getString("list_type");
                LOGGER.info("list_type Получен из БД");
            }
        } catch (SQLException e) {
            LOGGER.error("editKeyboardAfterLeanedOrForgot Ошибка получения list_type из БД");
            throw new RuntimeException(e);
        }

        return list_type;
    }

    /**
     * Метод получает из БД значение таймера для определенного пользователя и слова.
     * В случае ошибки выбрасывает исключение.
     *
     * @param word объект слова
     * @return значение таймера
     */
    public @NotNull Integer getRepetitionsCount(Word word) {
        nullCheck.checkForNull("getRepetitionsCount ", word);

        Connection connection = DatabaseConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT timer_value FROM user_word_lists WHERE user_id = ? AND word_id = ?")) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setInt(2, word.getWordId());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                LOGGER.info("Timer Value успешно получен");
                return resultSet.getInt("timer_value");
            } else
                throw new SQLException();
        } catch (SQLException e) {
            LOGGER.error("ОШИБКА получения timer_value из БД");
            throw new RuntimeException(e);
        }
    }
}
