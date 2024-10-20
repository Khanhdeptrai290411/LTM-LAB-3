// Updated MultiCastClient.java
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class MultiCastClient {
    private String userName;
    private JFrame loginFrame;
    private JFrame roomFrame;
    private JFrame chatFrame;

    private Socket serverSocket;
    private BufferedReader in;
    private PrintWriter out;

    private List<Room> rooms = new ArrayList<>();

    private JTable roomTable;
    private DefaultTableModel roomTableModel;

    private Room currentRoom;
    private JTextArea chatArea;
    private JTextField messageField;

    private Thread serverListenerThread;
    private volatile boolean initialRoomListLoaded = false;
    private final Object roomListLock = new Object();
    private volatile Room createdRoom = null;
    private final Object createRoomLock = new Object();

    public static void main(String[] args) {
        new MultiCastClient().showLoginInterface();
    }

    private void showLoginInterface() {
        loginFrame = new JFrame("Login");
        JTextField nameField = new JTextField(20);
        JButton loginButton = new JButton("Login");
        loginFrame.setLayout(new FlowLayout());
        loginFrame.add(new JLabel("Enter your name:"));
        loginFrame.add(nameField);
        loginFrame.add(loginButton);
        loginFrame.setSize(300, 150);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);

        loginButton.addActionListener(e -> {
            userName = nameField.getText().trim();
            if (!userName.isEmpty()) {
                loginFrame.dispose();
                connectToServer();
                startServerListener();
                showRoomInterface();
            } else {
                JOptionPane.showMessageDialog(loginFrame, "Please enter your name.");
            }
        });
    }

    private void connectToServer() {
        String serverHost = "172.20.10.2"; // Adjust as necessary
        int serverPort = 12344;

        try {
            serverSocket = new Socket(serverHost, serverPort);
            in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
            out = new PrintWriter(serverSocket.getOutputStream(), true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Unable to connect to server.");
            System.exit(1);
        }
    }

    private void startServerListener() {
        serverListenerThread = new Thread(() -> {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    if (response.startsWith("NewRoom")) {
                        String[] tokens = response.split(" ", 6);
                        int id = Integer.parseInt(tokens[1]);
                        String name = tokens[2];
                        String creator = tokens[3];
                        String multicastAddress = tokens[4];
                        int port = Integer.parseInt(tokens[5]);
                        Room room = new Room(id, name, creator, InetAddress.getByName(multicastAddress), port);
                        rooms.add(room);

                        SwingUtilities.invokeLater(() -> {
                            roomTableModel.addRow(new Object[]{id, name, creator});
                        });
                    } else if (response.startsWith("RoomCreated")) {
                        String[] tokens = response.split(" ", 6);
                        int id = Integer.parseInt(tokens[1]);
                        String name = tokens[2];
                        String creator = tokens[3];
                        String multicastAddress = tokens[4];
                        int port = Integer.parseInt(tokens[5]);
                        Room room = new Room(id, name, creator, InetAddress.getByName(multicastAddress), port);
                        rooms.add(room);

                        SwingUtilities.invokeLater(() -> {
                            roomTableModel.addRow(new Object[]{id, name, creator});
                        });

                        if (creator.equals(userName)) {
                            synchronized (createRoomLock) {
                                createdRoom = room;
                                createRoomLock.notifyAll();
                            }
                        }
                    } else if (response.startsWith("Room")) {
                        String[] tokens = response.split(" ", 6);
                        int id = Integer.parseInt(tokens[1]);
                        String name = tokens[2];
                        String creator = tokens[3];
                        String multicastAddress = tokens[4];
                        int port = Integer.parseInt(tokens[5]);
                        Room room = new Room(id, name, creator, InetAddress.getByName(multicastAddress), port);
                        rooms.add(room);

                        SwingUtilities.invokeLater(() -> {
                            roomTableModel.addRow(new Object[]{id, name, creator});
                        });
                    } else if (response.startsWith("Message")) {
                        String message = response.substring(8);
                        SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));
                    } else if (response.startsWith("PrivateMessage")) {
                        String message = response.substring(15);
                        SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));
                    } else if (response.startsWith("System")) {
                        String message = response;
                        SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));
                    } else if (response.equals("EndOfRoomList")) {
                        synchronized (roomListLock) {
                            initialRoomListLoaded = true;
                            roomListLock.notifyAll();
                        }
                    } else if (response.startsWith("ClearUserList")) {
//                        SwingUtilities.invokeLater(() -> userComboBox.removeAllItems());
                    } else if (response.startsWith("User")) {
                        String user = response.substring(5);
//                        SwingUtilities.invokeLater(() -> userComboBox.addItem(user));
                    } else if (response.equals("EndOfUserList")) {
                        // End of user list received, no action needed
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverListenerThread.start();
    }

    private void showRoomInterface() {
        roomFrame = new JFrame("Room List - " + userName);
        roomFrame.setLayout(new BorderLayout());

        roomTableModel = new DefaultTableModel(new Object[]{"Room ID", "Room Name", "Creator"}, 0);
        roomTable = new JTable(roomTableModel);
        JScrollPane scrollPane = new JScrollPane(roomTable);

        JButton createRoomButton = new JButton("Create Room");
        JButton joinRoomButton = new JButton("Join Room");

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);

        roomFrame.add(scrollPane, BorderLayout.CENTER);
        roomFrame.add(buttonPanel, BorderLayout.SOUTH);

        roomFrame.setSize(500, 300);
        roomFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        roomFrame.setLocationRelativeTo(null);
        roomFrame.setVisible(true);

        loadRoomList();

        createRoomButton.addActionListener(e -> {
            String roomName = JOptionPane.showInputDialog(roomFrame, "Enter room name:");
            if (roomName != null && !roomName.trim().isEmpty()) {
                createRoom(roomName.trim());
            }
        });

        joinRoomButton.addActionListener(e -> {
            int selectedRow = roomTable.getSelectedRow();
            if (selectedRow >= 0) {
                Room room = rooms.get(selectedRow);
                joinRoom(room);
                roomFrame.dispose();
                showChatInterface();
            } else {
                JOptionPane.showMessageDialog(roomFrame, "Please select a room to join.");
            }
        });
    }

    private void loadRoomList() {
        roomTableModel.setRowCount(0);
        rooms.clear();
        initialRoomListLoaded = false;

        out.println("GetRooms");

        synchronized (roomListLock) {
            while (!initialRoomListLoaded) {
                try {
                    roomListLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void createRoom(String roomName) {
        out.println("CreateRoom " + roomName + " " + userName);

        synchronized (createRoomLock) {
            while (createdRoom == null) {
                try {
                    createRoomLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        joinRoom(createdRoom);
        roomFrame.dispose();
        showChatInterface();
        createdRoom = null;
    }

    private void joinRoom(Room room) {
        currentRoom = room;
        out.println("JoinRoom " + room.getName() + " " + userName);
    }

    private void showChatInterface() {
        chatFrame = new JFrame("Chat Room - " + currentRoom.getName() + " - " + userName);
        chatFrame.setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        messageField = new JTextField();
        JButton sendButton = new JButton("Send");
        JButton leaveButton = new JButton("Leave Room");

//        userComboBox = new JComboBox<>();
//        userComboBox.addItem("All");

        JPanel inputPanel = new JPanel(new BorderLayout());
//        inputPanel.add(userComboBox, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        bottomPanel.add(leaveButton, BorderLayout.SOUTH);

        chatFrame.add(scrollPane, BorderLayout.CENTER);
        chatFrame.add(bottomPanel, BorderLayout.SOUTH);

        chatFrame.setSize(500, 400);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setLocationRelativeTo(null);
        chatFrame.setVisible(true);

        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        leaveButton.addActionListener(e -> leaveRoom());
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
                out.println("SendMessage " + "All" + " " + message);
                messageField.setText("");
        }
    }

    private void leaveRoom() {
        if (currentRoom != null) {
            out.println("LeaveRoom");
            currentRoom = null;
        }
        chatFrame.dispose();
        showRoomInterface();
    }

    private static class Room {
        private int id;
        private String name;
        private String creator;
        private InetAddress multicastAddress;
        private int port;

        public Room(int id, String name, String creator, InetAddress multicastAddress, int port) {
            this.id = id;
            this.name = name;
            this.creator = creator;
            this.multicastAddress = multicastAddress;
            this.port = port;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getCreator() { return creator; }
        public InetAddress getMulticastAddress() { return multicastAddress; }
        public int getPort() { return port; }
    }
}