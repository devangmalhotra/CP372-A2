import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private static volatile boolean handshakeCompleted = false;
    private static volatile boolean running = true;
    static int expectedSeqNum = 1;

    public static void main(String argv[]) throws Exception {
        DatagramSocket datagramSocket = null;
        int port = 0;

        try {
            port = Integer.parseInt(argv[2]);
            if (port < 1024 || port > 65535) {
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
                byte[] dataBytes = packetToSend.toBytes();
                DatagramPacket datagramForSot = new DatagramPacket(dataBytes, dataBytes.length, InetAddress.getByName(argv[0]), Integer.parseInt(argv[1]));

                int timeoutTimerHandshake = 0;

                while (!handshakeCompleted) {
                    datagramSocket.send(datagramForSot);

                    try {
                        byte[] buffer = new byte[128];
                        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                        datagramSocket.receive(dp);

                        DSPacket packet = new DSPacket(buffer);
                        byte packetType = packet.getType();
                        int packetSeqNum = packet.getSeqNum();

                        if (packetType == DSPacket.TYPE_ACK && packetSeqNum == 0) {
                            handshakeCompleted = true;
                        }
                    } catch (SocketTimeoutException e) {
                        timeoutTimerHandshake++;
                        if (timeoutTimerHandshake == 3) {
                            fis.close();
                            datagramSocket.close();
                            return;
                        }
                    }
                }

                byte[] bufferforFile = new byte[124];
                int readBytes;
                boolean emptyFile = (fis.available() == 0);
                boolean isGBN = (argv.length >= 6);

                if (!isGBN) { // Stop & Wait
                    if (!emptyFile) {
                        while ((readBytes = fis.read(bufferforFile)) != -1) {
                            DSPacket packet = new DSPacket(DSPacket.TYPE_DATA, expectedSeqNum, Arrays.copyOf(bufferforFile, readBytes));
                            byte[] packetBytes = packet.toBytes();
                            DatagramPacket sendingDatagramPacket = new DatagramPacket(packetBytes, packetBytes.length, InetAddress.getByName(argv[0]), Integer.parseInt(argv[1]));

                            boolean ackReceivedSuccessfully = false;
                            int timeoutTimer = 0;

                            while (!ackReceivedSuccessfully) {
                                datagramSocket.send(sendingDatagramPacket);

                                try {
                                    byte[] bufferForAck = new byte[128];
                                    DatagramPacket dpForAck = new DatagramPacket(bufferForAck, bufferForAck.length);
                                    datagramSocket.receive(dpForAck);
                                    DSPacket packetForAck = new DSPacket(bufferForAck);

                                    int ackPacketSeqNum = packetForAck.getSeqNum();
                                    byte ackPacketType = packetForAck.getType();

                                    if (ackPacketSeqNum == expectedSeqNum && ackPacketType == DSPacket.TYPE_ACK) {
                                        ackReceivedSuccessfully = true;
                                        timeoutTimer = 0;
                                        expectedSeqNum = (expectedSeqNum + 1) % 128;
                                    }
                                } catch (SocketTimeoutException e) {
                                    timeoutTimer++;
                                    if (timeoutTimer == 3) {
                                        fis.close();
                                        datagramSocket.close();
                                        return;
                                    }
                                }
                            }
                        }
                    }

                    // Send EOT
                    DSPacket packetForEOT = new DSPacket(DSPacket.TYPE_EOT, expectedSeqNum, null);
                    byte[] bytesForEOT = packetForEOT.toBytes();
                    DatagramPacket datagramForEOT = new DatagramPacket(bytesForEOT, bytesForEOT.length, InetAddress.getByName(argv[0]), Integer.parseInt(argv[1]));

                    boolean eotAckSuccessful = false;
                    int timeoutTimer = 0;

                    while (!eotAckSuccessful) {
                        datagramSocket.send(datagramForEOT);

                        try {
                            byte[] bufferForEOT = new byte[128];
                            DatagramPacket dpForEOT = new DatagramPacket(bufferForEOT, bufferForEOT.length);
                            datagramSocket.receive(dpForEOT);

                            DSPacket packetForAck = new DSPacket(bufferForEOT);
                            int ackPacketSeqNum = packetForAck.getSeqNum();
                            byte ackPacketType = packetForAck.getType();

                            if (ackPacketType == DSPacket.TYPE_ACK && ackPacketSeqNum == expectedSeqNum) {
                                eotAckSuccessful = true;
                            }
                        } catch (SocketTimeoutException e) {
                            timeoutTimer++;
                            if (timeoutTimer == 3) {
                                fis.close();
                                datagramSocket.close();
                                return;
                            }
                        }
                    }

                } else {
                    // GBN
                    int windowSize = Integer.parseInt(argv[5]);

                    List<DSPacket> allPackets = new ArrayList<>();
                    byte[] fileBuf = new byte[124];
                    int seqCounter = 1;

                    while ((readBytes = fis.read(fileBuf)) != -1) {
                        allPackets.add(new DSPacket(DSPacket.TYPE_DATA, seqCounter, Arrays.copyOf(fileBuf, readBytes)));
                        seqCounter = (seqCounter + 1) % 128;
                    }

                    int totalPackets = allPackets.size();
                    int base = 0;
                    int nextToSend = 0;
                    int timeoutCounter = 0;

                    InetAddress rcvAddr = InetAddress.getByName(argv[0]);
                    int rcvPort = Integer.parseInt(argv[1]);

                    while (base < totalPackets) {
                        if (nextToSend < base + windowSize && nextToSend < totalPackets) {
                            int groupStart = (nextToSend / 4) * 4;
                            int groupEnd = Math.min(groupStart + 4, totalPackets);

                            if ((groupEnd - groupStart) == 4) {
                                List<DSPacket> group = new ArrayList<>(4);
                                for (int i = groupStart; i < groupEnd; i++) group.add(allPackets.get(i));
                                List<DSPacket> permuted = ChaosEngine.permutePackets(group);
                                for (DSPacket pkt : permuted) {
                                    byte[] pktBytes = pkt.toBytes();
                                    datagramSocket.send(new DatagramPacket(pktBytes, pktBytes.length, rcvAddr, rcvPort));
                                    System.out.println("[GBN Sender] Sent DATA seq=" + pkt.getSeqNum());
                                }
                                nextToSend = groupEnd;
                            } else {
                                for (int i = nextToSend; i < groupEnd; i++) {
                                    DSPacket pkt = allPackets.get(i);
                                    byte[] pktBytes = pkt.toBytes();
                                    datagramSocket.send(new DatagramPacket(pktBytes, pktBytes.length, rcvAddr, rcvPort));
                                    System.out.println("[GBN Sender] Sent DATA seq=" + pkt.getSeqNum());
                                }
                                nextToSend = groupEnd;
                            }
                        }

                        // Wait for cumulative ACK
                        try {
                            byte[] ackBuf = new byte[128];
                            DatagramPacket ackDp = new DatagramPacket(ackBuf, ackBuf.length);
                            datagramSocket.receive(ackDp);
                            DSPacket ackPkt = new DSPacket(ackBuf);

                            if (ackPkt.getType() == DSPacket.TYPE_ACK) {
                                int ackedSeq = ackPkt.getSeqNum();
                                System.out.println("[GBN Sender] Received cumulative ACK seq=" + ackedSeq);

                                int baseSeq = allPackets.get(base).getSeqNum();
                                int dist = (ackedSeq - baseSeq + 128) % 128;

                                if (dist < windowSize + 1) {
                                    int newBase = base;
                                    for (int i = base; i < totalPackets; i++) {
                                        newBase = i + 1;
                                        if (allPackets.get(i).getSeqNum() == ackedSeq) break;
                                    }
                                    if (newBase > base) {
                                        base = newBase;
                                        timeoutCounter = 0;
                                    }
                                }
                            }
                        } catch (SocketTimeoutException e) {
                            timeoutCounter++;
                            
                            if (timeoutCounter >= 3) {
                                fis.close();
                                datagramSocket.close();
                                return;
                            }
                            nextToSend = base; // retransmit entire window
                        }
                    }

                    // Send EOT after GBN completes
                    DSPacket packetForEOT = new DSPacket(DSPacket.TYPE_EOT, expectedSeqNum, null);
                    byte[] bytesForEOT = packetForEOT.toBytes();
                    DatagramPacket datagramForEOT = new DatagramPacket(bytesForEOT, bytesForEOT.length, rcvAddr, rcvPort);

                    boolean eotAckSuccessful = false;
                    int timeoutTimer = 0;

                    while (!eotAckSuccessful) {
                        datagramSocket.send(datagramForEOT);

                        try {
                            byte[] bufferForEOT = new byte[128];
                            DatagramPacket dpForEOT = new DatagramPacket(bufferForEOT, bufferForEOT.length);
                            datagramSocket.receive(dpForEOT);

                            DSPacket packetForAck = new DSPacket(bufferForEOT);
                            int ackPacketSeqNum = packetForAck.getSeqNum();
                            byte ackPacketType = packetForAck.getType();

                            if (ackPacketType == DSPacket.TYPE_ACK && ackPacketSeqNum == expectedSeqNum) {
                                eotAckSuccessful = true;
                            }
                        } catch (SocketTimeoutException e) {
                            timeoutTimer++;
                            if (timeoutTimer == 3) {
                                fis.close();
                                datagramSocket.close();
                                return;
                            }
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