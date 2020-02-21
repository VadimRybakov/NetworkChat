package core;

import java.sql.*;

public class SqlClient {

    private static Connection connection;
    private static Statement statement;

    synchronized static void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:chat-server/chat.db");
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized static void disconnect() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized static String getNickname(String login, String password) {
        String query = String.format("select nickname from users where login='%s' and password='%s'", login, password);
        try (ResultSet set = statement.executeQuery(query)) {
            if (set.next())
                return set.getString(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    synchronized static boolean containsLogin(String login) {
        String query = String.format("select *from users where login='%s'", login);
        try (ResultSet set = statement.executeQuery(query)) {
            return set.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized static boolean containsNickname(String nickname) {
        String query = String.format("select *from users where nickname='%s'", nickname);
        try (ResultSet set = statement.executeQuery(query)) {
            return set.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized static int insertNewUser(String login, String password, String nickname) {
        String query = String.format("insert into users (login, password, nickname)" +
                " values ('%s', '%s', '%s')", login, password, nickname);
        try {
            return statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
