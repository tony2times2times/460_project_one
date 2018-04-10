import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.*;

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
	static ArrayList<DataPacket> dataPackets =  new ArrayList<DataPacket>();

	/** Default Values */
	private static final String DEFAULT_HOSTNAME = "localhost";
	private static final int DEFAULT_PORT = 0;
	private static final int DEFAULT_PACKET_SIZE = 64;
	private static final int DEFAULT_TIMEOUT = 2000;
	private static final int DEFAULT_WINDOW = 1;
	private static final double DEFAULT_DATAGRAM = 0.25;
	private static final String DEFAULT_FILEPATH = "test.txt";
	private static final int SERVER_PORT = 3000;
	private static final int OVERHEAD = 16;
	private static final int ACK_ONLY_PACKET_SIZE = 8;

	/** Logger */
	//private static final Logger audit = Logger.getLogger("requests");
	private static final Logger errors = Logger.getLogger("errors");

	/** Private members */
	private static short sizeOfPacket;
	private static int payloadSize;
	private static int timeoutInterval;
	private static int sizeOfWindow;
	private static int[] window;
	private static int nextPacketSeqno = 1;
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

	/**
	 * Server Main
	 */
	public static void main(String args[]) {
		readArgs(args);
		byte[] data = readFile();
		setDataPackets(data, fileLength);
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
	private static void setDataPackets(byte[] data, int fileLength) {
		// pointer to transfer data onto arrays of binary data segments
		int pointer = 0;
		// checksum of IP packet: good by default
		short checksum = 0;
		// acknowledge number of packet
		int ackno = 1;
		// sequence number of packet
		int seqno = 1;
		// if the file is not perfectly divisible by payload add a packet to hold
		// the rest
		int totalPackets = (fileLength % payloadSize == 0) ? (fileLength / payloadSize)
				: ((fileLength / payloadSize) + 1);
		int size = payloadSize;
		for (int i = 0; i < totalPackets; i++) {			
			boolean endOfData = i == (totalPackets - 1);
			if (endOfData) {
				size = data.length - pointer;
			}
			// Data portion of packet
			byte[] dataSegment = new byte[sizeOfPacket];
			System.arraycopy(data, pointer, dataSegment, 0, size);
			pointer += size;
			DataPacket dataPacket = new DataPacket(checksum, ackno, seqno++, totalPackets, dataSegment);
			dataPackets.add(dataPacket);
		}
	}

	/**
	 * needs to be updated! Prints packet number, start off-set and end off-set of a
	 * packet
	 * 
	 * @param packet
	 *            that holds data segment
	 */
	private static void printDataPacket(byte[] dataSegment) {

		// get the cksum in overhead
		byte[] cksum = new byte[2];
		System.arraycopy(dataSegment, 0, cksum, 0, 2);
		// buffer to turn byte to short
		ByteBuffer buffer = ByteBuffer.wrap(cksum);
		short cksumShort = buffer.getShort();

		// get the seqno in overhead
		byte[] seqnoByte = new byte[4];
		System.arraycopy(dataSegment, 8, seqnoByte, 0, 4);
		// buffer to turn byte to short
		ByteBuffer buffer2 = ByteBuffer.wrap(seqnoByte);
		int seqno = buffer2.getInt();

		int start = (seqno - 1) * payloadSize + 1;
		int end = (seqno - 1) * payloadSize + payloadSize;

		long time = System.currentTimeMillis();

		if (cksumShort == 0) {
			System.out.println("SENDing " + seqno + " " + start + ":" + end + " " + time + " SENT");
			return;
		} else if (cksumShort == 1) {
			System.out.println("SENDing " + seqno + " " + start + ":" + end + " " + time + " ERR");
		} else {
			System.out.println("SENDing " + seqno + " " + start + ":" + end + " " + time + " DROP");
		}
	}

	private static void printAckPacket(byte[] dataSegment) {

		if (dataSegment == null)
			return;

		// get the cksum in overhead
		byte[] cksum = new byte[2];
		System.arraycopy(dataSegment, 0, cksum, 0, 2);
		// buffer to turn byte to short
		ByteBuffer buffer = ByteBuffer.wrap(cksum);
		short cksumShort = buffer.getShort();

		// get the ackno in overhead
		byte[] acknoByte = new byte[4];
		System.arraycopy(dataSegment, 4, acknoByte, 0, 4);
		// buffer to turn byte to short
		ByteBuffer buffer2 = ByteBuffer.wrap(acknoByte);
		int ackno = buffer2.getInt();

	//	long time = System.currentTimeMillis();

		if (cksumShort == 0) {
			System.out.println("AckRcvd " + ackno + "MoveWnd");
			return;
		} else if (cksumShort == 1) {
			System.out.println("AckRcvd " + ackno + "ErrAck.");
		}
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
			// dropped packet
			short cksumSht = 2;
			interruptedSegment = dataPacket.getData().clone();
			byte[] cksum = ByteBuffer.allocate(2).putShort(cksumSht).array();
			System.arraycopy(cksum, 0, interruptedSegment, 0, TWO_BYTE);
			interruptedPacket = new DatagramPacket(interruptedSegment, interruptedSegment.length);
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
		initializeWindow();
		try (DatagramSocket socket = new DatagramSocket(SERVER_PORT)) {
			waitForRequest(socket);
			while (!socket.isClosed()) {
				if (windowOpen()) {
					sendNextPacket(socket);
					updateWindow();
				} else {
					waitForAck(socket);
				}
				boolean allPacketsSent = nextPacketSeqno > dataPackets.size();
				if (allPacketsSent) {
					// close the server socket
					socket.close();
				}
			}
		} catch (IOException | RuntimeException ex) {
			errors.log(Level.SEVERE, ex.getMessage(), ex);
		}
	}

	private static void updateWindow() {

	}

	private static boolean windowOpen() {
		for (int seqno : window) {
			if (seqno == 0) {
				return true;
			}
		}
		return false;
	}

	private static void waitForAck(DatagramSocket socket) throws IOException {
		byte[] clientSegment = new byte[ACK_ONLY_PACKET_SIZE];
		DatagramPacket clientPacket = new DatagramPacket(clientSegment, clientSegment.length);
		socket.receive(clientPacket);
		AckPacket ack = new AckPacket(clientSegment);
		for (int seqno : window) {
			if (seqno == ack.getAckno()) {
				seqno = nextPacketSeqno;
			}
		}
		printAckPacket(clientPacket.getData());
	}

	private static void sendNextPacket(DatagramSocket socket) throws IOException {
		byte[] nextPacket = dataPackets.get(nextPacketSeqno++).toDataSegment();
		DatagramPacket dataPacket = new DatagramPacket(nextPacket, nextPacket.length, clientAddress, clientPort);
		// interrupt the packet - requirement for class project simulating
		// dropped/corrupt packets.
		dataPacket = interruptPacket(dataPacket);
		// timeout will be caught
		try {
			if (isPacketIntact(dataPacket)) {
				// packet may be corrupted but is not dropped so send
				socket.send(dataPacket);
				printDataPacket(dataPacket.getData());
				socket.setSoTimeout(timeoutInterval);
			} else {
				// packet is dropped so do not send packet
				printDataPacket(dataPacket.getData());
			}
		} catch (SocketTimeoutException e) {
			printTimeout(nextPacketSeqno);
		}
	}

	private static void initializeWindow() {
		window = new int[sizeOfWindow];
		// fill window with values starting at 1 which is the first expected packet
		for (int i = 0; i < window.length; i++) {
			window[i] = nextPacketSeqno++;
		}
	}

	private static void waitForRequest(DatagramSocket socket) throws IOException {
		// receive request from the client
		byte[] clientSegment = new byte[ACK_ONLY_PACKET_SIZE];
		DatagramPacket clientPacket = new DatagramPacket(clientSegment, clientSegment.length);
		socket.receive(clientPacket);

		// update the receiver port and address
		clientAddress = clientPacket.getAddress();
		clientPort = clientPacket.getPort();
	}

	private static boolean isPacketIntact(DatagramPacket dataPacket) {
		// get the data
		byte[] dataSegment = dataPacket.getData();
		// get the cksum of overhead
		byte[] cksum = new byte[2];
		System.arraycopy(dataSegment, 0, cksum, 0, 2);
		// buffer to turn byte to short
		ByteBuffer buffer = ByteBuffer.wrap(cksum);
		short cksumShort = buffer.getShort();

		// check Short
		if (cksumShort == 0) {
			return true;
		}
		return false;
	}
}