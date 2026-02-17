
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.io.*;

public class Receiver {
    int expectedSeqNum = 0;
    int ackCount = 0; // to be used with the ChaosEngine
    private static volatile boolean handshakeCompleted = false;
    private static volatile boolean running = true;

    public static void main(String argv[]) throws Exception {
        // Creating the datagram socket
        DatagramSocket datagramSocket = null;
        int port = 0;

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
                FileOutputStream fos = new FileOutputStream(argv[3]);

                while(!handshakeCompleted) {
                    byte[] buffer = new byte[128]; // since each UDP datagram should be 128 bytes
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    datagramSocket.receive(dp);

                    DSPacket packet = new DSPacket(buffer);
                    byte packetType = packet.getType();
                }
                
            } catch (IOException e) {
                System.out.println("An error occurred: " + e.getMessage());
            }



        } catch (SocketException e) {
            if (running) {
                System.err.println("Socket error: " + e.getMessage());
            }
        }

    }
    
}