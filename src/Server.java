import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.*;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * The server reads a stream of data (from a file), breaks it into fixed-sized
 * packets suitable for UDP transport, prepends a control header to the data,
 * and sends each packet to the client.
 */

public class Server {
	static Options options = new Options();
	static CommandLineParser parser = new DefaultParser();
	static HelpFormatter formatter = new HelpFormatter();
	static CommandLine cmd = null;
	static int fileLength = 0;
	static ArrayList<byte[]> dataSegments;

	/** Default Values */
	private static final String DEFAULT_HOSTNAME = "localhost";
	private static final int DEFAULT_PORT = 0;
	private static final int DEFAULT_PACKET_SIZE = 64;
	private static final int DEFAULT_TIMEOUT = 2000;
	private static final int DEFAULT_WINDOW = 1;
	private static final double DEFAULT_DATAGRAM = 0.25;
	private static final String DEFAULT_FILEPATH = "test.txt";
	private static final int SERVER_PORT = 3000;
	private static final int OVERHEAD = 12;
	private static final int ACK_ONLY_PACKET_SIZE = 8;

	/** Logger */
	private static final Logger audit = Logger.getLogger("requests");
	private static final Logger errors = Logger.getLogger("errors");

	/** Private members */
	private static short sizeOfPacket;
	private static int payloadSize;
	private static int timeoutInterval;
	private static int sizeOfWindow;
	private static double datagramPercentage;
	private static int clientPort;
	private static InetAddress clientAddress;
	private static String filePath;

	/** Explanatory Variables */
	private static final String OPTION_SIZE_SHORT = "s";
	private static final String OPTION_SIZE = "size";
	private static final String OPTION_SIZE_DESCRIPTION = "size of packet";
	private static final String OPTION_TIMEOUT_SHORT = "t";
	private static final String OPTION_TIMEOUT = "timeout";
	private static final String OPTION_TIMEOUT_DESCRIPTION = "timeout interval";
	private static final String OPTION_WINDOW_SHORT = "w";
	private static final String OPTION_WINDOW = "window";
	private static final String OPTION_WINDOW_DESCRIPTION = "sliding window size";
	private static final String OPTION_DATAGRAM_SHORT = "d";
	private static final String OPTION_DATAGRAM = "datagram";
	private static final String OPTION_DATAGRAM_DESCRIPTION = "percentage of datagram to corrupt, delay, or drop";
	private static final String OPTION_HELP_SHORT = "h";
	private static final String OPTION_HELP = "help";
	private static final String OPTION_HELP_DESCRIPTION = "show options";
	private static final String OPTION_FILE_SHORT = "f";
	private static final String OPTION_FILE = "file";
	private static final String OPTION_FILE_DESCRIPTION = "file path";
	private static final int TWO_BYTE = 2;
	private static final int FOUR_BYTE = 4;

	/**
	 * Server Main
	 */
	public static void main(String args[]) {
		readArgs(args);
		byte[] data = readFile();
		dataSegments = getDataSegments(data, fileLength);
		sendPackets();
	}

	private static void readArgs(String args[]) {
		defineCommandLineOptions();
		parseCommandLineArguments(args);
		setSize();
		setTimeOut();
		setWindow();
		setDatagram();
		setFile();
		setClient();
		displayHelp();
	}

	/**
	 * Divides the data and feeds it into packets along with the overhead
	 * 
	 * @param byte
	 *            array that holds all data
	 * @return data segments ArrayList
	 */
	private static ArrayList<byte[]> getDataSegments(byte[] data, int fileLength) {

		// array list to hold arrays of binary data segments
		ArrayList<byte[]> dataSegments = new ArrayList<byte[]>();

		// if the file is not perfectly divisible by payload add a packet to hold
		// the rest
		int numPackets = (fileLength % payloadSize == 0) ? (fileLength / payloadSize) : ((fileLength / payloadSize) + 1);

		// pointer to transfer data onto arrays of binary data segments
		int payloadPointer = 0;
		// checksum of IP packet: good by default
		short cksumSht = 0;
		// acknowledge number of packet
		int acknoInt = 1;
		// sequence number of packet
		int seqnoInt = 1;

		/**
		 * Fill each packet
		 */
		for (int i = 0; i < numPackets; i++) {
			// pointer to transfer overhead
			int overheadPointer = 0;

			byte[] dataSegment = new byte[sizeOfPacket];

			/**
			 * Overhead
			 */
			// cksum
			byte[] cksum = ByteBuffer.allocate(2).putShort(cksumSht).array();
			System.arraycopy(cksum, 0, dataSegment, overheadPointer, TWO_BYTE);
			overheadPointer += TWO_BYTE;
			// len
			byte[] len = ByteBuffer.allocate(2).putShort(sizeOfPacket).array();
			System.arraycopy(len, 0, dataSegment, overheadPointer, TWO_BYTE);
			overheadPointer += TWO_BYTE;
			// ackno
			byte[] ackno = ByteBuffer.allocate(4).putInt(acknoInt++).array();
			System.arraycopy(ackno, 0, dataSegment, overheadPointer, FOUR_BYTE);
			overheadPointer += FOUR_BYTE;
			// seqno
			byte[] seqno = ByteBuffer.allocate(4).putInt(seqnoInt++).array();
			System.arraycopy(seqno, 0, dataSegment, overheadPointer, FOUR_BYTE);
			overheadPointer += FOUR_BYTE;

			/**
			 * Payload
			 */
			for (int j = OVERHEAD; j < sizeOfPacket; j++) {
				if (payloadPointer < fileLength) {
					dataSegment[j] = data[payloadPointer++];
				}
			}
			dataSegments.add(dataSegment);
		}
		return dataSegments;
	}


	/**
	 * needs to be updated! Prints packet number, start off-set and end off-set of a
	 * packet
	 * 
	 * @param packet
	 *            that holds data segment
	 */
	private static void printPacket(byte[] dataSegment) {

		int currentPacket = (int) dataSegment[0];
		int start = (currentPacket - 1) * payloadSize + 1;
		int end = (currentPacket - 1) * payloadSize + payloadSize;

		audit.info("[packet#" + dataSegment[0] + "]-" + "[" + start + "]-" + "[" + end + "]\n");
	}

	/**
	 * Prints timeout information on the console
	 */
	private static void printTimeout(int i) {
		System.out.println("Timeout " + i);
	}

	/**
	 * Simulates a lossy network by randomly corrupting or dropping the given packet
	 * The chance is determined by the datagram percentage variable
	 * 
	 * @param dataPacket
	 * @return interruptedPacket
	 */
	private static DatagramPacket interruptPacket(DatagramPacket dataPacket) {
		byte[] interruptedSegment = new byte[dataPacket.getData().length];
		DatagramPacket interruptedPacket = null;
		double chance = Math.random();
		boolean isDropped = chance < datagramPercentage / 2;
		boolean isCorrupted = (chance < datagramPercentage) && (!isDropped);

		if (isDropped) {
			// dropped packet is null
		} else if (isCorrupted) {
			// corrupted packet has bad checksum
			short cksumSht = 1;
			interruptedSegment = dataPacket.getData().clone();
			byte[] cksum = ByteBuffer.allocate(2).putShort(cksumSht).array();
			System.arraycopy(cksum, 0, interruptedSegment, 0, TWO_BYTE);
			interruptedPacket = new DatagramPacket(interruptedSegment, interruptedSegment.length);
		} else {
			// packet is intact
			interruptedPacket = dataPacket;
		}
		return interruptedPacket;
	}

	private static void defineCommandLineOptions() {
		// size option
		Option sizeOption = new Option(OPTION_SIZE_SHORT, OPTION_SIZE, true, OPTION_SIZE_DESCRIPTION);
		sizeOption.setRequired(false);
		options.addOption(sizeOption);
		// timeout option
		Option timeoutOption = new Option(OPTION_TIMEOUT_SHORT, OPTION_TIMEOUT, true, OPTION_TIMEOUT_DESCRIPTION);
		timeoutOption.setRequired(false);
		options.addOption(timeoutOption);
		// window option
		Option windowOption = new Option(OPTION_WINDOW_SHORT, OPTION_WINDOW, true, OPTION_WINDOW_DESCRIPTION);
		windowOption.setRequired(false);
		options.addOption(windowOption);
		// datagram option
		Option datagramOption = new Option(OPTION_DATAGRAM_SHORT, OPTION_DATAGRAM, true, OPTION_DATAGRAM_DESCRIPTION);
		datagramOption.setRequired(false);
		options.addOption(datagramOption);
		// file option
		Option fileOption = new Option(OPTION_FILE_SHORT, OPTION_FILE, true, OPTION_FILE_DESCRIPTION);
		fileOption.setRequired(false);
		options.addOption(fileOption);
		// help option
		Option helpOption = new Option(OPTION_HELP_SHORT, OPTION_HELP, false, OPTION_HELP_DESCRIPTION);
		helpOption.setRequired(false);
		options.addOption(helpOption);
	}

	private static void parseCommandLineArguments(String args[]) {
		try {
			// parse options into command line
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			// print exception and help then exit
			System.out.println(e.getMessage());
			formatter.printHelp(Server.class.getSimpleName(), options);
			System.exit(1);
		}
	}

	private static void setSize() {
		if (cmd.hasOption(OPTION_SIZE)) {
			// size of packet specified by user
			sizeOfPacket = Short.parseShort(cmd.getOptionValue(OPTION_SIZE));
			payloadSize = sizeOfPacket - OVERHEAD;
		} else {
			// size of packet set to default
			sizeOfPacket = DEFAULT_PACKET_SIZE;
			payloadSize = sizeOfPacket - OVERHEAD;
		}
	}

	private static void setTimeOut() {
		if (cmd.hasOption(OPTION_TIMEOUT)) {
			// timeout interval specified by user
			timeoutInterval = Integer.parseInt(cmd.getOptionValue(OPTION_TIMEOUT));
		} else {
			// timeout interval set to default
			timeoutInterval = DEFAULT_TIMEOUT;
		}
	}

	private static void setWindow() {
		if (cmd.hasOption(OPTION_WINDOW)) {
			// sliding window size specified by user
			sizeOfWindow = Integer.parseInt(cmd.getOptionValue(OPTION_WINDOW));
		} else {
			// sliding window size set to default
			sizeOfWindow = DEFAULT_WINDOW;
		}
	}

	private static void setDatagram() {
		if (cmd.hasOption(OPTION_DATAGRAM)) {
			// datagram percentage specified by user
			datagramPercentage = Double.parseDouble(cmd.getOptionValue(OPTION_DATAGRAM));
		} else {
			// datagram percentage set to default
			datagramPercentage = DEFAULT_DATAGRAM;
		}
	}

	private static void setFile() {
		if (cmd.hasOption(OPTION_FILE)) {
			// file path specified by user
			filePath = cmd.getOptionValue(OPTION_FILE);
		} else {
			// file path set to default
			filePath = DEFAULT_FILEPATH;
		}
	}

	private static void displayHelp() {
		if (cmd.hasOption(OPTION_HELP)) {
			// help asked from the user
			formatter.printHelp(Server.class.getSimpleName(), options);
			System.exit(0);
		} else {
			// help is not asked
		}
	}

	private static void setClient() {
		if (!cmd.getArgList().isEmpty()) {
			// receiver address set by user
			byte[] hostAddress = cmd.getArgList().iterator().next().getBytes();
			try {
				clientAddress = InetAddress.getByAddress(hostAddress);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			// receiver port set by user
			clientPort = Integer.parseInt(cmd.getArgList().iterator().next());
		} else {
			// receiver port set to default
			try {
				clientAddress = InetAddress.getByName(DEFAULT_HOSTNAME);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			// receiver address set to default
			clientPort = DEFAULT_PORT;
		}
	}

	private static byte[] readFile() {
		// initialize file object
		File file = new File(filePath);
		// initialize file size
		fileLength = (int) file.length();
		// initialize data segment array
		byte[] data = new byte[fileLength];
		FileInputStream in;
		try {
			// read the file and store binary data into data array
			in = new FileInputStream(file);
			in.read(data);
			in.close();
			return data;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Establish server socket Receive a request from a client Send the packets to
	 * the requester Close the server socket
	 */
	private static void sendPackets() {
		try (DatagramSocket datagramSocket = new DatagramSocket(SERVER_PORT)) {
			while (!datagramSocket.isClosed()) {
				try {
					// receive request from the client
					byte[] requestSegment = new byte[ACK_ONLY_PACKET_SIZE];
					DatagramPacket requestPacket = new DatagramPacket(requestSegment, requestSegment.length);
					datagramSocket.receive(requestPacket);

					// update the receiver port and address
					clientAddress = requestPacket.getAddress();
					clientPort = requestPacket.getPort();

					// prepare ack-only packet from client
					byte[] ackOnlySegment = new byte[ACK_ONLY_PACKET_SIZE];
					DatagramPacket ackOnlyPacket = new DatagramPacket(ackOnlySegment, ackOnlySegment.length);

					// Send each data packet
					for (int i = 0; i < dataSegments.size(); i++) {
						// repeat until positive acknowledgement is received
						while (true) {
							// create a data packet
							DatagramPacket dataPacket = new DatagramPacket(dataSegments.get(i),
									dataSegments.get(i).length, clientAddress, clientPort);
							// interrupt the packet
							dataPacket = interruptPacket(dataPacket);
							// timeout will be caught
							try {
								if (dataPacket != null) {
									// packet may be corrupted but is not dropped so send
									datagramSocket.send(dataPacket);
									printPacket(dataPacket.getData());
									datagramSocket.setSoTimeout(timeoutInterval);
									datagramSocket.receive(ackOnlyPacket);
									printPacket(ackOnlyPacket.getData());
									// if ackno is positive break the while loop
								} else {
									// packet is dropped so do not send packet
									printPacket(null);
								}
							} catch (SocketTimeoutException e) {
								printTimeout(i);
							}
						}
					}
					// close the server socket
					datagramSocket.close();
				} catch (IOException | RuntimeException ex) {
					errors.log(Level.SEVERE, ex.getMessage(), ex);
				}
			}
		} catch (IOException ex) {
			errors.log(Level.SEVERE, ex.getMessage(), ex);
		}
	}

}