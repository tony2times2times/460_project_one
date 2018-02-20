import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Sender {

	private static final int PORT = 13;
	
	// host name varies depending on the PC
	private static final String HOSTNAME = "sckang511-HP-Compaq-6200-Pro-SFF-PC";
	
	// number of packets to send
	private static final int MAX_PACKET = 12;
	
	public static void main(String args) {
		try (DatagramSocket socket = new DatagramSocket(0)) {
			
			// establish host
			socket.setSoTimeout(10000);
			InetAddress host = InetAddress.getByName(HOSTNAME);
			
			// read the binary file
			File file = new File(args);
			int fileSize = (int) file.length();
			byte[] binary = new byte[fileSize];
			DataInputStream dis = new DataInputStream(new FileInputStream(file));
			dis.readFully(binary);
			dis.close();
			
			// divide the binary into twelve packets
			DatagramPacket[] packets = new DatagramPacket[MAX_PACKET];
			int chunkSize = fileSize / MAX_PACKET;
			int newSize = fileSize / MAX_PACKET;
			byte[] chunk = new byte[chunkSize];
			for(int i = 0; i < MAX_PACKET; i ++) {
				for(int j = 0 + (i * newSize); j < newSize; j ++) {
					chunk[j] = binary[j];
				}
				packets[i] = new DatagramPacket(chunk, chunkSize, host, PORT);
				newSize += chunkSize;
			}
			
			// create response packets
			DatagramPacket[] responses = new DatagramPacket[MAX_PACKET];
			for(int i = 0; i < MAX_PACKET; i ++) {
				responses[i] = new DatagramPacket(new byte[1], 1);
			}
			
			// send binary packets
			for(int i = 1; i <= MAX_PACKET; i ++) {
				System.out.println("Sending packet#" + i + "...");
				socket.send(packets[i - 1]);
				System.out.println(
						"Packet#" + i + 
					" containing binary[" + ((i - 1) * chunkSize) + " - " + (i * chunkSize)+ "] " +
					"sent successfully.");
				socket.receive(responses[i]);
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
