
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.io.*;

public class Receiver {
    static int expectedSeqNum = 0;
    static int ackCount = 0; // to be used with the ChaosEngine
    static int lastDelivered = 0;
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
        System.out.println("Receiver started on port " + argv[2]);

        try {
            datagramSocket = new DatagramSocket(port);

            try {
                FileOutputStream fos = new FileOutputStream(argv[3]);

                while(!handshakeCompleted) { // just for first packet
                    byte[] buffer = new byte[128]; // since each UDP datagram should be 128 bytes
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    datagramSocket.receive(dp);

                    DSPacket packet = new DSPacket(buffer);
                    byte packetType = packet.getType();
                    int packetSeqNum = packet.getSeqNum();

                    if (packetType == DSPacket.TYPE_SOT && packetSeqNum == 0) {
                        DSPacket newPacket = new DSPacket(DSPacket.TYPE_ACK, 0, null);
                        System.out.println("Receiver: Successful handshake. Received SOT with seqNum 0, sending ACK with seqNum 0");

                        // Checking if packet should be dropped with ChaosEngine
                        int rn = Integer.parseInt(argv[4]);
                        boolean shouldDropResult = ChaosEngine.shouldDrop(ackCount, rn);

                        if (!shouldDropResult) {
                            byte [] dataBytes = newPacket.toBytes();
                            DatagramPacket ackDatagramPacket = new DatagramPacket(dataBytes, dataBytes.length, InetAddress.getByName(argv[0]), Integer.parseInt(argv[1]));
                            datagramSocket.send(ackDatagramPacket);
                            handshakeCompleted = true;
                            expectedSeqNum = 1;
                            ackCount++;
                        }

                    }
                }

                while(running) {
                    byte[] buffer = new byte[128]; // since each UDP datagram should be 128 bytes
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    datagramSocket.receive(dp);

                    DSPacket packet = new DSPacket(buffer);
                    byte packetType = packet.getType();
                    int packetSeqNum = packet.getSeqNum();

                    if (packetType == DSPacket.TYPE_DATA) {
                        System.out.println("Receiver: Received data with seqNum " + packetSeqNum);

                        if(packetSeqNum == expectedSeqNum) {
                            fos.write(packet.getPayload());
                            lastDelivered = packetSeqNum;
                            expectedSeqNum = (expectedSeqNum + 1) % 128; // since it needs to wrap around

                            System.out.println("Receiver: Acknowledging data with seqNum " + packetSeqNum);
                        } else {
                            System.out.println("Receiver: Packet is out of order with seqNum " + packetSeqNum + ". resending ACK for seqNum " + lastDelivered);
                        }

                        // Checking if packet should be dropped with ChaosEngine
                        int rn = Integer.parseInt(argv[4]);
                        boolean shouldDropResult = ChaosEngine.shouldDrop(ackCount, rn);

                        if (!shouldDropResult) {
                            ackCount++; // increase ack count only when an ack is sent
                            DSPacket packetWithAck = new DSPacket(DSPacket.TYPE_ACK, lastDelivered, null);
                            byte [] dataBytes = packetWithAck.toBytes();
                            DatagramPacket ackDatagramPacket = new DatagramPacket(dataBytes, dataBytes.length, InetAddress.getByName(argv[0]), Integer.parseInt(argv[1]));
                            datagramSocket.send(ackDatagramPacket);
                        }
                    } else if (packetType == DSPacket.TYPE_EOT) {
                        System.out.println("Receiver: Received EOT with seqNum " + packetSeqNum + ". Sending ACK");

                        // Checking if packet should be dropped with ChaosEngine
                        int rn = Integer.parseInt(argv[4]);
                        boolean shouldDropResult = ChaosEngine.shouldDrop(ackCount, rn);

                        if (!shouldDropResult) {
                            DSPacket packetWithAck = new DSPacket(DSPacket.TYPE_ACK, packetSeqNum, null);
                            byte [] dataBytes = packetWithAck.toBytes();
                            DatagramPacket ackDatagramPacket = new DatagramPacket(dataBytes, dataBytes.length, InetAddress.getByName(argv[0]), Integer.parseInt(argv[1]));
                            datagramSocket.send(ackDatagramPacket);
                        }
                        running = false;
                        fos.close();
                    }

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