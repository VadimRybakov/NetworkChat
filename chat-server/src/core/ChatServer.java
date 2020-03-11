package core;

import common.Library;
import network.ServerSocketThread;
import network.ServerSocketThreadListener;
import network.SocketThread;
import network.SocketThreadListener;

import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Vector;
import org.apache.log4j.Logger;

public class ChatServer implements ServerSocketThreadListener, SocketThreadListener {

    private final ChatServerListener listener;
    private ServerSocketThread server;
    private final DateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss: ");
    private Vector<SocketThread> clients = new Vector<>();
    private static final Logger log = Logger.getLogger(ChatServer.class);

    public ChatServer(ChatServerListener listener) {
        this.listener = listener;
    }

    public void start(int port) {
        if (server != null && server.isAlive()) {
            putLog("Server already started");
            log.debug("Server already started");
        }
        else
            server = new ServerSocketThread(this, "Server", port, 2000);
    }

    public void stop() {
        if (server == null || !server.isAlive()) {
            putLog("Server is not running");
            log.debug("Server is not running");
        } else {
            server.interrupt();
        }
    }

    private void putLog(String msg) {
        msg = DATE_FORMAT.format(System.currentTimeMillis()) +
                Thread.currentThread().getName() + ": " + msg;
        listener.onChatServerMessage(msg);
    }

    /**
     * Server methods
     *
     * */

    @Override
    public void onServerStart(ServerSocketThread thread) {
        putLog("Server thread started");
        log.debug("Server thread started");
        SqlClient.connect();
    }

    @Override
    public void onServerStop(ServerSocketThread thread) {
        putLog("Server thread stopped");
        log.debug("Server thread stopped");
        SqlClient.disconnect();
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).close();
        }

    }

    @Override
    public void onServerSocketCreated(ServerSocketThread thread, ServerSocket server) {
        putLog("Server socket created");
        log.debug("Server socket created");
    }

    @Override
    public void onServerTimeout(ServerSocketThread thread, ServerSocket server) {
//        putLog("Server timeout");

    }

    @Override
    public void onSocketAccepted(ServerSocketThread thread, ServerSocket server, Socket socket) {
        putLog("Client connected");
        log.trace("Client with ip: " + socket.getInetAddress() + " port: " + socket.getPort() + " connected");
        String name = "SocketThread " + socket.getInetAddress() + ":" + socket.getPort();
        new ClientThread(this, name, socket);

    }

    @Override
    public void onServerException(ServerSocketThread thread, Throwable exception) {
        log.error("exception " + exception);
        exception.printStackTrace();
    }

    /**
     * Socket methods
     *
     * */

    @Override
    public synchronized void onSocketStart(SocketThread thread, Socket socket) {
        putLog("Socket created");
        log.debug("Socket created");
    }

    @Override
    public synchronized void onSocketStop(SocketThread thread) {
        ClientThread client = (ClientThread) thread;
        clients.remove(thread);
        if (client.isAuthorized() && !client.isReconnecting() ) {
            sendToAuthClients(Library.getTypeBroadcast("Server",
                    client.getNickname() + " disconnected"));
        }
        sendToAuthClients(Library.getUserList(getUsers()));
    }

    @Override
    public synchronized void onSocketReady(SocketThread thread, Socket socket) {
        clients.add(thread);
    }

    @Override
    public synchronized void onReceiveString(SocketThread thread, Socket socket, String msg) {
        ClientThread client = (ClientThread) thread;
        if (client.isAuthorized()) {
            handleAuthMessage(client, msg);
        } else {
            handleNonAuthMessage(client, msg);
        }
    }

    private void handleNonAuthMessage(ClientThread client, String msg) {
        String[] arr = msg.split(Library.DELIMITER);
        if(arr.length == 4 && arr[0].equals(Library.SIGN_UP_REQUEST)){
            String login = arr[1];
            if(login == null) {
                putLog("Empty login ");
                log.warn("Empty login ");
                client.signUpFail();
                return;
            }
            String nickname = arr[2];
            if(nickname == null) {
                putLog("Empty nickname ");
                log.warn("Empty nickname ");
                client.signUpFail();
                return;
            }
            String password = arr[3];
            if(password == null) {
                putLog("Empty password ");
                log.warn("Empty password ");
                client.signUpFail();
                return;
            }
            if(SqlClient.containsLogin(login)) {
                putLog("This login is already exist: " + login);
                log.warn("This login is already exist: "+ login);
                client.authFail();
                return;
            }
            if(SqlClient.containsNickname(nickname)) {
                putLog("This nickname is already exist: " + nickname);
                log.warn("This nickname is already exist: "+ nickname);
                client.authFail();
                return;
            }
            if(SqlClient.insertNewUser(login, password, nickname) == 0) {
                putLog("error while sign up, try again");
                log.warn("error while sign up, try again");
                client.signUpFail();
            } else {
                client.authAccept(nickname);
                sendToAuthClients(Library.getTypeBroadcast("Server", nickname + " connected"));
                sendToAuthClients(Library.getUserList(getUsers()));
            }
        }
        else if (arr.length == 3 && arr[0].equals(Library.AUTH_REQUEST)) {
            String login = arr[1];
            String password = arr[2];
            String nickname = SqlClient.getNickname(login, password);
            if (nickname == null) {
                putLog("Invalid login attempt: " + login);
                log.warn("Invalid login attempt: " + login);
                client.authFail();
                return;
            } else {
                ClientThread oldClient = findClientByNickname(nickname);
                client.authAccept(nickname);
                if (oldClient == null) {
                    sendToAuthClients(Library.getTypeBroadcast("Server", nickname + " connected"));
                } else {
                    oldClient.reconnect();
                    clients.remove(oldClient);
                }

            }
            sendToAuthClients(Library.getUserList(getUsers()));
        } else client.msgFormatError(msg);
    }

    private void handleAuthMessage(ClientThread client, String msg) {
        String[] arr = msg.split(Library.DELIMITER);
        String msgType = arr[0];
        switch (msgType) {
            case Library.TYPE_BCAST_CLIENT:
                sendToAuthClients(Library.getTypeBroadcast(
                        client.getNickname(), arr[1]));
                log.info(client.getNickname() + " " + arr[1]);
                break;
            default:
                client.sendMessage(Library.getMsgFormatError(msg));
        }
    }
    private void sendToAuthClients(String msg) {
        for (int i = 0; i < clients.size(); i++) {
            ClientThread client = (ClientThread) clients.get(i);
            if (!client.isAuthorized()) continue;
            client.sendMessage(msg);
        }
    }

    @Override
    public synchronized void onSocketException(SocketThread thread, Exception exception) {
        exception.printStackTrace();
    }

    private String getUsers() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clients.size(); i++) {
            ClientThread client = (ClientThread) clients.get(i);
            if (!client.isAuthorized()) continue;
            sb.append(client.getNickname()).append(Library.DELIMITER);
        }
        return sb.toString();
    }

    private synchronized ClientThread findClientByNickname(String nickname) {
        for (int i = 0; i < clients.size(); i++) {
            ClientThread client = (ClientThread) clients.get(i);
            if (!client.isAuthorized()) continue;
            if (client.getNickname().equals(nickname))
                return client;
        }
        return null;
    }


}
