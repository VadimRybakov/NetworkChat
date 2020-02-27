import common.Library;
import network.SocketThread;
import network.SocketThreadListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class ClientGUI extends JFrame implements ActionListener, Thread.UncaughtExceptionHandler, SocketThreadListener {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 300;

    private final JTextArea log = new JTextArea();
    private final JPanel panelTop = new JPanel(new GridLayout(2, 4));
    private final JTextField tfIPAddress = new JTextField("192.168.1.42");
    private final JTextField tfPort = new JTextField("8189");
    private final JCheckBox cbAlwaysOnTop = new JCheckBox("Always on top");
    private JTextField tfLogin = new JTextField();
    private JPasswordField tfPassword = new JPasswordField();
    private final JButton btnLogin = new JButton("Login");
    private final JButton btnSignUp = new JButton("SignUp");

    private final JPanel panelBottom = new JPanel(new BorderLayout());
    private final JButton btnDisconnect = new JButton("<html><b>Disconnect</b></html>");
    private final JTextField tfMessage = new JTextField();
    private final JButton btnSend = new JButton("Send");

    private final JPanel signUpPanel = new JPanel(new GridLayout(2,2));
    private final JTextField tfSignUpLogin = new JTextField();
    private final JTextField tfSignUpNickname = new JTextField();
    private final JPasswordField tfSignUpPassword = new JPasswordField();
    private boolean isSignedUp = false;

    private final JList<String> userList = new JList<>();
    private boolean shownIoErrors = false;
    private SocketThread socketThread;
    private final DateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss: ");
    private final String WINDOW_TITLE = "Chat";

    private ClientGUI() {
        Thread.setDefaultUncaughtExceptionHandler(this);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setTitle(WINDOW_TITLE);
        setSize(WIDTH, HEIGHT);
        log.setEditable(false);
        log.setLineWrap(true);
        JScrollPane scrollLog = new JScrollPane(log);
        JScrollPane scrollUser = new JScrollPane(userList);
        scrollUser.setPreferredSize(new Dimension(100, 0));
        cbAlwaysOnTop.addActionListener(this);
        btnSend.addActionListener(this);
        tfMessage.addActionListener(this);
        btnLogin.addActionListener(this);
        btnSignUp.addActionListener(this);
        btnDisconnect.addActionListener(this);
        panelBottom.setVisible(false);

        panelTop.add(tfIPAddress);
        panelTop.add(tfPort);
        panelTop.add(btnSignUp);
        panelTop.add(cbAlwaysOnTop);
        panelTop.add(tfLogin);
        panelTop.add(tfPassword);
        panelTop.add(btnLogin);

        panelBottom.add(btnDisconnect, BorderLayout.WEST);
        panelBottom.add(tfMessage, BorderLayout.CENTER);
        panelBottom.add(btnSend, BorderLayout.EAST);

        signUpPanel.add(new JLabel("login", SwingConstants.LEFT));
        signUpPanel.add(new JLabel("nickname", SwingConstants.LEFT));
        signUpPanel.add(new JLabel("password", SwingConstants.LEFT));
        signUpPanel.add(tfSignUpLogin);
        signUpPanel.add(tfSignUpNickname);
        signUpPanel.add(tfSignUpPassword);

        add(scrollLog, BorderLayout.CENTER);
        add(scrollUser, BorderLayout.EAST);
        add(panelTop, BorderLayout.NORTH);
        add(panelBottom, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void connect() {
        try {
            Socket socket = new Socket(tfIPAddress.getText(), Integer.parseInt(tfPort.getText()));
            socketThread = new SocketThread(this, "Client", socket);
        } catch (IOException e) {
            showException(Thread.currentThread(), e);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() { // Event Dispatching Thread
                new ClientGUI();
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == cbAlwaysOnTop) {
            setAlwaysOnTop(cbAlwaysOnTop.isSelected());
        } else if (src == btnSend || src == tfMessage) {
            sendMessage();
        } else if (src == btnLogin) {
            connect();
        } else if (src == btnSignUp) {
            signUp();
        } else if (src == btnDisconnect) {
            socketThread.close();
        } else {
            throw new RuntimeException("Unknown source: " + src);
        }
    }

    private void signUp(){
        JOptionPane.showConfirmDialog(
            this, signUpPanel, "signUp", JOptionPane.OK_CANCEL_OPTION);
        isSignedUp = true;
        connect();
    }

    private void sendMessage() {
        try {
            String msg = tfMessage.getText();
            String username = tfLogin.getText();
            if ("".equals(msg)) return;
            tfMessage.setText(null);
            tfMessage.requestFocusInWindow();
            socketThread.sendMessage(Library.getTypeBcastClient(msg));
            wrtMsgToLogFile(msg, username);
        } catch (IOException e) {
            if (!shownIoErrors) {
                shownIoErrors = true;
                showException(Thread.currentThread(), e);
            }
        }
    }

    private void wrtMsgToLogFile(String msg, String username) {
        try (FileWriter out = new FileWriter("log.txt", true)) {
            out.write(username + ": " + msg + "\n");
            out.flush();
        } catch (IOException e) {
            if (!shownIoErrors) {
                shownIoErrors = true;
                showException(Thread.currentThread(), e);
            }
        }
    }

    private void loadHistory(){
        try (RandomAccessFile randomAccessFile = new RandomAccessFile("log.txt", "r")){
            //если лог файл в несколько Гб, то быстрее будет читать его ближе к 100 последним строкам
            // 100 * 1024 - должно хватить на 100 строк
            int numberOfLastLength = 100;
            if(randomAccessFile.length() > numberOfLastLength * 1024)
                randomAccessFile.seek(randomAccessFile.length() - numberOfLastLength * 1024);
            else
                randomAccessFile.seek(0);
            ArrayList<String> arr = new ArrayList<>();
            String line;
            do {
                line = randomAccessFile.readLine();
                arr.add(line);
            }
            while(line != null);
            int temp = arr.size() - 1;
            if(numberOfLastLength > temp) numberOfLastLength = temp;
            for (int i = temp - numberOfLastLength; i  < temp; i++) {
                putLog(arr.get(i));
            }
        } catch (IOException | NullPointerException e) {
            if (!shownIoErrors) {
                shownIoErrors = true;
                showException(Thread.currentThread(), e);
            }
        }
    }

    private void putLog(String msg) {
        if ("".equals(msg)) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                log.append(msg + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            }
        });
    }

    private void showException(Thread t, Throwable e) {
        String msg;
        StackTraceElement[] ste = e.getStackTrace();
        if (ste.length == 0)
            msg = "Empty Stacktrace";
        else {
            msg = "Exception in " + t.getName() + " " +
                    e.getClass().getCanonicalName() + ": " +
                    e.getMessage() + "\n\t at " + ste[0];
        }
        JOptionPane.showMessageDialog(null, msg, "Exception", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
        showException(t, e);
        System.exit(1);
    }

    /**
     * Socket thread listener methods
     * */

    @Override
    public void onSocketStart(SocketThread thread, Socket socket) {
        putLog("Start");
    }

    @Override
    public void onSocketStop(SocketThread thread) {
        panelBottom.setVisible(false);
        panelTop.setVisible(true);
        setTitle(WINDOW_TITLE);
        userList.setListData(new String[0]);
    }

    @Override
    public void onSocketReady(SocketThread thread, Socket socket) {
        panelBottom.setVisible(true);
        panelTop.setVisible(false);
        if(!isSignedUp) {
            String login = tfLogin.getText();
            String password = new String(tfPassword.getPassword());
            thread.sendMessage(Library.getAuthRequest(login, password));
        } else {
            tfLogin.setText(null);
            tfPassword.setText(null);
            String signUpLogin = tfSignUpLogin.getText();
            String signUpNickname = tfSignUpNickname.getText();
            String signUpPassword = new String(tfSignUpPassword.getPassword());
            thread.sendMessage(Library.getSignUpRequest(signUpLogin, signUpNickname, signUpPassword));
            isSignedUp = false;
            tfLogin = tfSignUpLogin;
            tfPassword = tfSignUpPassword;
        }
        loadHistory();

    }

    @Override
    public void onReceiveString(SocketThread thread, Socket socket, String msg) {
        handleMessage(msg);
    }

    @Override
    public void onSocketException(SocketThread thread, Exception exception) {
//         showException(thread, exception);
    }

    private void handleMessage(String msg) {
        String[] arr = msg.split(Library.DELIMITER);
        String msgType = arr[0];
        switch (msgType) {
            case Library.SIGN_UP_DENIED:
            case Library.AUTH_DENIED:
                putLog(msg);
                break;
            case Library.AUTH_ACCEPT:
                setTitle(WINDOW_TITLE + " entered with nickname: " + arr[1]);
                break;
            case Library.MSG_FORMAT_ERROR:
                putLog(msg);
                socketThread.close();
                break;
            case Library.TYPE_BROADCAST:
                putLog(DATE_FORMAT.format(Long.parseLong(arr[1])) +
                        arr[2] + ": " + arr[3]);
                break;
            case Library.USER_LIST:
                String users = msg.substring(Library.USER_LIST.length() +
                        Library.DELIMITER.length());
                String[] usersArr = users.split(Library.DELIMITER);
                Arrays.sort(usersArr);
                userList.setListData(usersArr);
                break;
            default:
                throw new RuntimeException("Unknown message type: " + msg);
        }
    }
}
