import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

/**
 * This class receives a binary file and sends to clients
 */
 
public class Server {

	/** The port */
	private static final int PORT = 3000;

	/** The packet size in bytes */
	private static final int PACKET_SIZE = 64;

	/** The over head reserved in bytes */
	private static final int OVERHEAD_SIZE = 2; 

	/** The payload size in bytes */
	private static int PAYLOAD_SIZE = PACKET_SIZE - OVERHEAD_SIZE;

	/** The audit */
	private static final Logger audit = Logger.getLogger("requests");

	/** The errors */
	private static final Logger errors = Logger.getLogger("errors");

	/**
	*	Server 
	*	Establishes a socket and sends the received file to clients upon request (single-threaded)
	*	@param filePath 
	*/
	public static void main(String args[]) {
		
		/** Receive the file path from cmd args */
		String filePath = null;
		if(args.length == 0) {
			System.out.println("Proper Usage: java Server <file_path>");
			System.exit(0);
		} else {
			filePath = args[0];
		}
		
		try ( DatagramSocket datagramSocket = new DatagramSocket(PORT) ) {
			while ( !datagramSocket.isClosed() ) {
			 	try {
					/** Receive a request */
					byte[] requestSegment = new byte[PACKET_SIZE + OVERHEAD_SIZE];
					DatagramPacket request = new DatagramPacket(requestSegment, requestSegment.length);
					datagramSocket.receive(request);
					
					/** Read the file */
					File file = new File(filePath);
					int fileLength = (int) file.length();
					byte[] data = new byte[fileLength];
					FileInputStream in = new FileInputStream(file);
					in.read(data);
					in.close();
					
					/** Divide the data into segments */
					ArrayList<byte[]> dataSegments = getDataSegments(data);
					
					/** Send the data packets and log */
					for (byte[] dataSegment : dataSegments) {
						DatagramPacket packet = new DatagramPacket( dataSegment, dataSegment.length, request.getAddress(), request.getPort() );
						datagramSocket.send(packet);
						printPacket(dataSegment);
					}
					
					/** Close the socket */
					datagramSocket.close();
				} catch (IOException | RuntimeException ex) {
					errors.log(Level.SEVERE, ex.getMessage(), ex);
				}
			}
		} catch (IOException ex) {
			errors.log(Level.SEVERE, ex.getMessage(), ex);
		}
	}


	/**
	*	Divides the data into data segments
	*	@param byte array that holds all data
	*	@return data segments arraylist
	*/
	private static ArrayList<byte[]> getDataSegments(byte[] data) {
		
		/** data segments to hold segments */
		ArrayList<byte[]> dataSegments = new ArrayList<byte[]>();
		int fileSize = data.length;
		
		/** if the file is not perfectly divisible by payload add a buffer byte to hold the rest */
		int numPackets = ( fileSize % PAYLOAD_SIZE == 0 ) ?
				( fileSize / PAYLOAD_SIZE) :
				( fileSize / PAYLOAD_SIZE + 1);	
		
		/** pointer at data */
		int dataPointer = 0;
		int currentPacket = 1;
		
		/** fill each packet */
		for (int i = 0; i < numPackets; i ++) {
			byte[] dataSegment = new byte[PACKET_SIZE];
			
			/** overhead */
			dataSegment[0] = (byte) currentPacket ++;
			dataSegment[1] = (byte) numPackets;
			
			/** payload */
			for (int j = OVERHEAD_SIZE; j < PACKET_SIZE; j ++) {
				if (dataPointer < fileSize) {
					dataSegment[j] = data[dataPointer ++];
				}
			}
			dataSegments.add(dataSegment);
		}
		return dataSegments;
	}

	/**
	*	Prints packet number, start off-set and end off-set of a packet
	*	@param packet that holds data segment
	*/
	private static void printPacket(byte[] dataSegment) {
		
		int currentPacket = (int) dataSegment[0];
		int start = (currentPacket - 1) * PAYLOAD_SIZE;
		int end = (currentPacket - 1) * PAYLOAD_SIZE + PAYLOAD_SIZE;

		audit.info("[packet#" + dataSegment[0] + "]-" + "[" + start + "]-" + "[" + end + "]\n");
	}
}
