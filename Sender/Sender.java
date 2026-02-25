import java.net.*;
import java.util.*;
import java.io.*;

public class Sender {
    private static volatile boolean handshakeCompleted = false;
    private static volatile boolean running = true;

    public static void main(String argv[]) throws Exception {
        // Creating the datagram socket
        DatagramSocket datagramSocket = null;
        int port = 0;
        
        // Getting port
        try {
            port = Integer.parseInt(argv[2]);
            if(port < 1024 || port > 65535) {
                System.err.println("Port must be between 1024 and 65535");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + argv[2]);
            System.exit(1);
        }

        try {
            datagramSocket = new DatagramSocket(port);

            try {
                FileInputStream fis = new FileInputStream(argv[3]);

            } catch (Exception e) {
                System.out.println("An error occurred: " + e.getMessage());
            }

        } catch (Exception e) {
            if (running) {
                System.err.println("Socket error: " + e.getMessage());
            }
        }
    }
    
}
