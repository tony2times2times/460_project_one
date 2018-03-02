import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {

	private static final int PORT = 3000;
	private static final Logger audit = Logger.getLogger("requests");
	private static final Logger errors = Logger.getLogger("errors");
	
	/** number of packets to send */
	private static final int MAX_PACKET = 12;
	
	/** total bytes reserved for overhead */
	private static final int OVERHEAD = 2;
	
	public static void main(String filePath) {
		
		try (DatagramSocket socket = new DatagramSocket(PORT)){
			while(true) {
				try {
					// read the binary file
					File file = new File(filePath);
					int fileSize = (int) file.length();
					byte[] binaryArray = new byte[fileSize];
					DataInputStream dis = new DataInputStream(new FileInputStream(file));
					dis.readFully(binaryArray);
					dis.close();
					
					// receive a request from the client
					DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
					socket.receive(request);
					
					// array to hold response packets 
					DatagramPacket[] responsePackets = new DatagramPacket[MAX_PACKET];
					
					// equally divided segment size of the file
					int segmentSize = (fileSize % MAX_PACKET == 0) ? 
							(fileSize / MAX_PACKET) : (fileSize / MAX_PACKET + 1);
							
					// total packet size considering the overhead
					int packetSize = segmentSize + OVERHEAD;

					// keep track of current packet and pointer at binary array
					int binaryPointer = 0;
					int currentPacket = 1;
					
					// byte array to hold each segment of file
					byte[] packet = new byte[packetSize];
					
					// initialize all the packets
					for(int i = 0; i < MAX_PACKET; i ++) {
						
						// overhead of packet
						packet[0] = (byte) currentPacket ++;
						packet[1] = MAX_PACKET;

						// segment of packet
						for(int j = 0; j < segmentSize; j ++) {
							packet[j + OVERHEAD] = binaryArray[binaryPointer++];
						}
						
						// add the packet to the response packet array
						responsePackets[i] = new DatagramPacket(packet, packetSize, 
								request.getAddress(), request.getPort());
					}
							
					// send all the response packets
					for(int i = 1; i <= MAX_PACKET; i ++) {
						System.out.println("Sending packet#" + i + "...");
						socket.send(responsePackets[i - 1]);
						System.out.println(
								"Packet#" + i + " containing binary[" + ((i - 1) * segmentSize) 
								+ " - " + (i * segmentSize)+ "] sent successfully.");
					}
	
				} catch(IOException | RuntimeException ex) {
					errors.log(Level.SEVERE, ex.getMessage(), ex);
				}
			}
		} catch(IOException ex) {
			errors.log(Level.SEVERE, ex.getMessage(), ex);
		}
	}
}
