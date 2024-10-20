// Updated MultiCastServer.java
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MultiCastServer {
    private static Map<Integer, Room> rooms = new ConcurrentHashMap<>();
    private static int roomIdCounter = 1;
    private static int multicastAddressCounter = 1;
    private static final String BASE_MULTICAST_ADDRESS = "230.0.0.";

    private static List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        int serverPort = 12344;

        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("MultiCast Server is running on port " + serverPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandlers.add(clientHandler);
                clientHandler.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private Room currentRoom;
        private String userName;

        public ClientHandler(Socket clientSocket) {
            this.socket = clientSocket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String request;
                while ((request = in.readLine()) != null) {
                    if (request.startsWith("GetRooms")) {
                        sendRoomList();
                    } else if (request.startsWith("CreateRoom")) {
                        String[] tokens = request.split(" ", 3);
                        if (tokens.length == 3) {
                            String roomName = tokens[1];
                            String creatorName = tokens[2];
                            createRoom(roomName, creatorName);
                        }
                    } else if (request.startsWith("JoinRoom")) {
                        String[] tokens = request.split(" ", 3);
                        if (tokens.length == 3) {
                            String roomName = tokens[1];
                            this.userName = tokens[2];
                            joinRoom(roomName);
                        }
                    } else if (request.startsWith("LeaveRoom")) {
                        leaveRoom();
                    } else if (request.startsWith("SendMessage")) {
                        String[] tokens = request.split(" ", 3);
                        if (tokens.length == 3) {
                            String recipient = tokens[1];
                            String message = tokens[2];
                            if (recipient.equals("All")) {
                                broadcastMessageToRoom(message);
                            } else {
                                sendPrivateMessage(recipient, message);
                            }
                        }
                    } else {
                        out.println("UnknownCommand");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                clientHandlers.remove(this);
                leaveRoom();
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void sendRoomList() {
            for (Room room : rooms.values()) {
                out.println("Room " + room.getId() + " " + room.getName() + " " + room.getCreator()
                        + " " + room.getMulticastAddress().getHostAddress() + " " + room.getPort());
            }
            out.println("EndOfRoomList");
        }

        private void createRoom(String roomName, String creatorName) {
            try {
                String multicastAddress = BASE_MULTICAST_ADDRESS + multicastAddressCounter++;
                InetAddress group = InetAddress.getByName(multicastAddress);
                int port = 5000 + roomIdCounter;
                Room room = new Room(roomIdCounter++, roomName, creatorName, group, port);
                rooms.put(room.getId(), room);
                out.println("RoomCreated " + room.getId() + " " + room.getName() + " "
                        + room.getCreator() + " " + room.getMulticastAddress().getHostAddress()
                        + " " + room.getPort());

                System.out.println(getCurrentTimeStamp() + " - User '" + creatorName + "' created room '" + roomName + "'");
                broadcastNewRoom(room);

            } catch (UnknownHostException e) {
                e.printStackTrace();
                out.println("Error Creating Room");
            }
        }

        private void joinRoom(String roomName) {
            for (Room room : rooms.values()) {
                if (room.getName().equals(roomName)) {
                    this.currentRoom = room;
                    room.addUser(userName);
                    out.println("JoinedRoom " + room.getId() + " " + room.getName());
                    System.out.println(getCurrentTimeStamp() + " - User '" + userName + "' joined room '" + roomName + "'");
                    broadcastSystemMessageToRoom("System - Người dùng '" + userName + "' đã vào phòng");
                    updateUserListInRoom();
                    return;
                }
            }
            out.println("RoomNotFound");
        }

        private void leaveRoom() {
            if (currentRoom != null) {
                currentRoom.removeUser(userName);
                System.out.println(getCurrentTimeStamp() + " - User '" + userName + "' left room '" + currentRoom.getName() + "'");
                broadcastSystemMessageToRoom("System - Người dùng '" + userName + "' đã rời phòng");
                updateUserListInRoom();
                currentRoom = null;
            }
        }

        private void broadcastMessageToRoom(String message) {
            if (currentRoom != null) {
                for (ClientHandler clientHandler : clientHandlers) {
                    if (clientHandler.currentRoom == this.currentRoom) {
                        clientHandler.out.println("Message [" + socket.getInetAddress().getHostAddress() + "] - " + userName + ": " + message);
                        System.out.println(getCurrentTimeStamp() + " - User '" + userName + "' has sent a message to room '" + currentRoom.getName() + "': '"+message+"'");
                    }
                }
            }
        }

        private void sendPrivateMessage(String recipient, String message) {
            if (recipient.equals(userName)) {
                out.println("Error: Cannot send private message to yourself.");
                return;
            }
            for (ClientHandler clientHandler : clientHandlers) {
                if (clientHandler.userName != null && clientHandler.userName.equals(recipient)) {
                    clientHandler.out.println("PrivateMessage From [" + socket.getInetAddress().getHostAddress() + "] - " + userName + ": " + message);
                    this.out.println("PrivateMessage To [" + socket.getInetAddress().getHostAddress() + "] - " + recipient + ": " + message);
                    System.out.println(getCurrentTimeStamp() + " - User '" + userName + "' has sent a private message to user '" + recipient + "': '"+message+ "' in room '"+currentRoom.getName()+"'");
                    return;
                }
            }
            out.println("UserNotFound " + recipient);
        }

        private void broadcastSystemMessageToRoom(String message) {
            if (currentRoom != null) {
                for (ClientHandler clientHandler : clientHandlers) {
                    if (clientHandler.currentRoom == this.currentRoom) {
                        clientHandler.out.println(message);
                        System.out.println(getCurrentTimeStamp() + " - System message: '" +message+"'");
                    }
                }
            }
        }

        private void updateUserListInRoom() {
            if (currentRoom != null) {
                for (ClientHandler clientHandler : clientHandlers) {
                    if (clientHandler.currentRoom == this.currentRoom) {
                        clientHandler.sendUserList();
                    }
                }
                System.out.println(getCurrentTimeStamp() + " - Roomlist has been sent to every users");
            }
        }

        private void sendUserList() {
            if (currentRoom != null) {
                out.println("ClearUserList");
                out.println("User All");
                for (String user : currentRoom.getUsers()) {
                    out.println("User " + user);
                }
                out.println("EndOfUserList");
            }
        }

        private void broadcastNewRoom(Room room) {
            String message = "NewRoom " + room.getId() + " " + room.getName() + " "
                    + room.getCreator() + " " + room.getMulticastAddress().getHostAddress()
                    + " " + room.getPort();
            for (ClientHandler clientHandler : clientHandlers) {
                if (clientHandler != this) {
                    clientHandler.out.println(message);
                }
            }
            
        }
    }

    private static String getCurrentTimeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static class Room {
        private int id;
        private String name;
        private String creator;
        private InetAddress multicastAddress;
        private int port;
        private Set<String> users = new HashSet<>();

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
        public Set<String> getUsers() { return users; }

        public void addUser(String user) { users.add(user); }
        public void removeUser(String user) { users.remove(user); }
    }
}