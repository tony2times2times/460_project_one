import java.io.*;
import java.net.*;
import java.util.*;

/**
 * This class sends the specified text or file as a datagram to the specified
 * port of the specified host.
 */
public class Server {

	/** The running. */
	private static boolean running = true;

	/** The running. */
	private static final boolean TEST = false;

	/** The file path. */
	private static String filePath;

	/** The port. */
	private static int port = 3000; // port to transmit on

	/** The packet size. */
	private static int packetSize = 64;// in bytes

	/** The over head. */
	private static int overHead = 2; // total bytes reserved for overhead

	/** The payload size. */
	private static int payloadSize = packetSize - overHead;

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 */
	public static void main(String args[]) {
		if (TEST) {
			filePath = ".\\in.txt";
		} else {
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				System.out.print("Enter path: ");
				filePath = reader.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		while (running) {
			try {
				InetAddress hostIp = InetAddress.getByName("localhost");
				File file = new File(filePath);
				int fileLength = (int) file.length();
				byte[] allData = new byte[fileLength];
				FileInputStream in = new FileInputStream(file);
				// reads all bytes from the file into the allData Array
				in.read(allData);
				in.close();
				// breaks the allData array into an array list of smaller byte
				// arrays
				ArrayList<byte[]> dataSegments = getDataSegments(allData);
				for (byte[] dataSegment : dataSegments) {
					DatagramPacket packet = new DatagramPacket(dataSegment, dataSegment.length, hostIp, port);
					DatagramSocket datagramSocket = new DatagramSocket();
					datagramSocket.send(packet);
					datagramSocket.close();
					printPacket(dataSegment);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Takes the byteArray containing all file data and converts it to an
	 * ArrayList of smaller byte arrays.
	 *
	 * @param allData
	 *            the all data
	 * @return the data segments
	 */
	private static ArrayList<byte[]> getDataSegments(byte[] allData) {
		int fileSize = allData.length;
		ArrayList<byte[]> dataSegments = new ArrayList<byte[]>();
		int numPackets;
		int currentPacket = 1;
		/*
		 * Determine if the file is perfectly divisible by the payload size. If
		 * it is not add an extra packet to cover the remaining data.
		 */
		if (fileSize % payloadSize != 0) {

			numPackets = fileSize / payloadSize + 1;
		} else {
			numPackets = fileSize / payloadSize;
		}
		// stores the last byte read in from allData
		int lastRead = 0;
		// for each packet
		for (int i = 0; i < numPackets; i++) {
			byte[] dataSegment = new byte[packetSize];
			// first byte stores the current packet
			dataSegment[0] = (byte) currentPacket++;
			// second byte stores the total number of packets
			dataSegment[1] = (byte) numPackets;
			// fills the payload portion of the data segment
			for (int j = overHead; j < packetSize; j++) {
				if (lastRead < fileSize) {
					dataSegment[j] = allData[lastRead];
					lastRead++;
				}
			}
			dataSegments.add(dataSegment);
		}
		return dataSegments;
	}

	/**
	 * Prints the info for each datagram received.
	 *
	 * @param dataSegment
	 *            the data segment
	 * @throws UnsupportedEncodingException
	 *             the unsupported encoding exception
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