import java.net.*;
import java.util.*;
import java.io.*;

public class Sender {
    private static volatile boolean handshakeCompleted = false;
    private static volatile boolean running = true;
    static int expectedSeqNum = 1;

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
            datagramSocket.setSoTimeout(Integer.parseInt(argv[4]));

            try {
                FileInputStream fis = new FileInputStream(argv[3]);

                // Sending the SOT packet to start handshake
                DSPacket packetToSend = new DSPacket(DSPacket.TYPE_SOT, 0, null);
                byte [] dataBytes = packetToSend.toBytes();
                DatagramPacket datagramForSot = new DatagramPacket(dataBytes, dataBytes.length, InetAddress.getByName(argv[0]), Integer.parseInt(argv[1]));

                int timeoutTimerHandshake = 0;

                while(!handshakeCompleted) {
                    datagramSocket.send(datagramForSot);

                    System.out.println("Sender: Sent SOT with seqNum 0. Now waiting for ACK");

                    try {
                        byte[] buffer = new byte[128]; // since each UDP datagram should be 128 bytes
                        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                        datagramSocket.receive(dp);

                        DSPacket packet = new DSPacket(buffer);
                        byte packetType = packet.getType();
                        int packetSeqNum = packet.getSeqNum();

                        if (packetType == DSPacket.TYPE_ACK && packetSeqNum == 0) {
                            handshakeCompleted = true;
                            System.out.println("Sender: Successful handshake. Received ACK with seqNum 0");
                        }
                    } catch (SocketTimeoutException e) {
                        timeoutTimerHandshake++;

                        if (timeoutTimerHandshake == 3) { //max reached
                            fis.close();
                            datagramSocket.close();
                            return;
                        }
                    }
                    
                }

                // Sending data after handshake has been completed
                byte[] bufferforFile = new byte[124]; // since each DATA packet carries exactly 124 bytes (except the final one)
                int readBytes;
                boolean emptyFile = (fis.available() == 0);

                if (!emptyFile) {
                    while((readBytes = fis.read(bufferforFile)) != -1) {
                        DSPacket packet = new DSPacket(DSPacket.TYPE_DATA, expectedSeqNum, Arrays.copyOf(bufferforFile, readBytes));
                        byte[] packetBytes = packet.toBytes();
                        DatagramPacket sendingDatagramPacket = new DatagramPacket(packetBytes, packetBytes.length, InetAddress.getByName(argv[0]), Integer.parseInt(argv[1]));

                        System.out.println("Sender: Sent data with seqNum " + expectedSeqNum + ". Now waiting for ACK");

                        // Now, wait for ack
                        boolean ackReceivedSuccessfully = false;
                        int timeoutTimer = 0;

                        while(!ackReceivedSuccessfully) {
                            datagramSocket.send(sendingDatagramPacket);

                            try {
                                byte[] bufferForAck = new byte[128];
                                DatagramPacket dpForAck = new DatagramPacket(bufferForAck, bufferForAck.length);
                                datagramSocket.receive(dpForAck);
                                DSPacket packetForAck = new DSPacket(bufferForAck);
                                
                                int ackPacketSeqNum = packetForAck.getSeqNum();
                                byte ackPacketType = packetForAck.getType();
                                
                                if(ackPacketSeqNum == expectedSeqNum && ackPacketType == DSPacket.TYPE_ACK) {
                                    ackReceivedSuccessfully = true;
                                    expectedSeqNum = (expectedSeqNum + 1) % 128; // since it needs to wrap around
                                    System.out.println("Receiver: Received ACK with seqNum " + ackPacketSeqNum);
                                }
                            } catch (SocketTimeoutException e) {
                                timeoutTimer++;

                                if (timeoutTimer == 3) { //max reached
                                    fis.close();
                                    datagramSocket.close();
                                    return;
                                }
                            }
                            
                        }

                    }
                }

                // EOT if last file
                DSPacket packetForEOT = new DSPacket(DSPacket.TYPE_EOT, expectedSeqNum, null);
                byte[] bytesForEOT = packetForEOT.toBytes();

                DatagramPacket datagramForEOT = new DatagramPacket(bytesForEOT, bytesForEOT.length, InetAddress.getByName(argv[0]), Integer.parseInt(argv[1]));

                boolean eotAckSuccessful = false;
                int timeoutTimer = 0;

                while(!eotAckSuccessful) {
                    datagramSocket.send(datagramForEOT);

                    System.out.println("Sender: Sent EOT with seqNum " + expectedSeqNum + ". Now waiting for ACK");

                    try {
                        byte[] bufferForEOT = new byte[128];
                        DatagramPacket dpForEOT= new DatagramPacket(bufferForEOT, bufferForEOT.length);

                        datagramSocket.receive(dpForEOT);

                        DSPacket packetForAck = new DSPacket(bufferForEOT);
                        int ackPacketSeqNum = packetForAck.getSeqNum();
                        byte ackPacketType = packetForAck.getType();

                        if (ackPacketType == DSPacket.TYPE_ACK && ackPacketSeqNum == expectedSeqNum) {
                            eotAckSuccessful = true;
                        }
                        

                    } catch (SocketTimeoutException e) {
                        timeoutTimer++;

                        if (timeoutTimer == 3) { //max reached
                            fis.close();
                            datagramSocket.close();
                            return;
                        }
                    }
                }
                
                fis.close();
                datagramSocket.close();

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
