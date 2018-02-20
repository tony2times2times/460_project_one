import java.io.*;
import java.net.*;
import java.util.ArrayList;

/**
 * The Class Client recieves datagrams and assembles a file from those datagrams
 */
public class Client {

	/** The running. */
	private static boolean running = true;

	/** The file path. */
	private static String filePath = ".\\out.txt"; //where the output file is written to

	/** The port. */
	private static int port = 3000; // port to transmit on

	/** The packet size. */
	private static int packetSize = 64;// in bytes

	/** The over head. */
	private static int overHead = 2; // total bytes reserved for overhead

	/** The payload size. */
	private static int payloadSize = packetSize - overHead;

	/** The next packet. */
	private static int nextPacket = 1; //start looking for the first packet


	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws Exception the exception
	 */
	public static void main(String[] args) throws Exception {
		InetAddress hostAddress = InetAddress.getByName("localhost");
		DatagramSocket datagramSocket = new DatagramSocket(port);
		ArrayList<byte[]> dataSegments = new ArrayList<byte[]>();

		while (running) {
			byte[] dataSegment = new byte[packetSize];
			DatagramPacket datagramPacket = new DatagramPacket(dataSegment, dataSegment.length);
			DatagramPacket out = new DatagramPacket(dataSegment, dataSegment.length, hostAddress, port);
			datagramSocket.send(out);

			datagramSocket.receive(datagramPacket);
			dataSegment = datagramPacket.getData();
			//look at the total number of packets and see if this is that last expected packet
			if (dataSegment[1] == nextPacket && dataSegment[1] != 0) {
				dataSegments.add(dataSegment);
				printPacket(dataSegment);
				PrintWriter fileOut = new PrintWriter(filePath);
				//convert ArrayList of byte arrays to a single byte array
				byte[] fileBytes=  getBytes(dataSegments);
				String payLoadText = new String(fileBytes, "UTF-8");
				//write file
				fileOut.println(payLoadText);
				fileOut.close();

				for (byte[] segment : dataSegments) {
					String testString = new String(segment, "UTF-8");
					System.out.println(testString);
				}

				//end client
				running = false;
			}
			if (dataSegment[0] == nextPacket) {
				dataSegments.add(dataSegment);
				nextPacket++;
				printPacket(dataSegment);
			}

		}
		datagramSocket.close();
	}

	/**
	 * Gets the bytes.
	 *
	 * @param dataSegments the data segments
	 * @return the bytes
	 */
	static byte[] getBytes(ArrayList<byte[]> dataSegments){
		int fileLength = payloadSize * dataSegments.size();
		byte[] fileBytes = new byte[fileLength];
		int nextByte = 0;//start from the first index
		//for each dataSegment
		for (byte[] dataSegment : dataSegments) {
			//Write the payload not including the overhead to the fileBytes array.
			for (int i = overHead; i < dataSegment.length; i++) {
				fileBytes[nextByte] = dataSegment[i];
				++nextByte;// move to the next byte
			}
		}
		return fileBytes;
	}

	/**
	 * Prints the info for each datagram received.
	 *
	 * @param dataSegment the data segment
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	static void printPacket(byte[] dataSegment) throws UnsupportedEncodingException {
		// log packet info
		int currentPacket = (int) dataSegment[0];
		int totalPackets = (int) dataSegment[1];
		int start = currentPacket * payloadSize;
		int end = currentPacket * payloadSize + payloadSize;
		String payLoadString = null;
		byte[] payLoadArray = new byte[payloadSize];
		for (int i = overHead; i < packetSize; i++) {
			payLoadString = payLoadString + dataSegment[i] + ", ";
			payLoadArray[i - overHead] = dataSegment[i];
		}
		// remove original null value and ending comma
		payLoadString = payLoadString.substring(4, payLoadString.length() - 2);
		String payLoadText = null;
		payLoadText = new String(payLoadArray, "UTF-8");
		System.out.println("total number of packets: " + totalPackets);
		System.out.println("Packet Number: " + currentPacket);
		System.out.println("Start byte offset: " + start);
		System.out.println("End byte offset: " + end);
		System.out.println("Byte sent: " + payLoadString);
		System.out.println("Text sent: ");
		System.out.println(payLoadText);
		System.out.println("*********************************************************");
	}
}