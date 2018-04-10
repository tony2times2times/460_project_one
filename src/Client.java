import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import java.util.logging.Logger;
import org.apache.commons.cli.*;

/**
 * The Class Client recieves datagrams and assembles a file from those datagrams
 */
public class Client {
	static Options options = new Options();
	static CommandLineParser parser = new DefaultParser();
	static HelpFormatter formatter = new HelpFormatter();
	static CommandLine cmd = null;
	static ArrayList<DataPacket> allPackets = new ArrayList<DataPacket>();
	private static int sizeOfWindow;
	private static int[] window;
	private static final int DEFAULT_WINDOW = 1;
	private static int nextPacketSeqno = 1;
	private static double datagramPercentage = 0.25;
	private static InetAddress serverAddress;
	private static int clientPort = 0;

	/** The running. */
	private static boolean running = true;

	/** The file path. */
	private static String filePath = ".\\out.txt"; // where the output file is written to

	/** The port. */
	private static int serverPort = 3000;

	/** The packet size. */
	private static int packetSize = 64;// in bytes

	/** The over head. */
	private static final int OVER_HEAD = 16; // total bytes reserved for overhead

	/** The payload size. */
	private static int payloadSize = packetSize - OVER_HEAD;

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 * @throws Exception
	 *             the exception
	 */
	public static void main(String[] args) throws Exception {
		readArgs(args);
		initializeWindow();
		DatagramSocket socket = new DatagramSocket(clientPort);
		// send blank packet to start connection with the server
		DatagramPacket request = new DatagramPacket(new byte[packetSize], packetSize, serverAddress, serverPort);
		socket.send(request);
		while (running) {
			byte[] dataSegment = getDataSegment(socket);
			DataPacket packet = new DataPacket(dataSegment);
			corruptPacket(packet);
			printIncomingPacket(packet);
			if (packet.isValid() && inWindow(packet)) {
				sendAck(packet.getAckno(), socket, serverAddress);
				allPackets.add(packet);
				// sort the packets by seqno in case they were received out of order
				allPackets.sort(Comparator.comparingInt(DataPacket::getSeqno));
				boolean allPacketsRecieved = packet.getTotalPackets() == allPackets.size();
				if (allPacketsRecieved) {
					byte[] fileBytes = getBytes();
					try (FileOutputStream fos = new FileOutputStream(filePath)) {
						fos.write(fileBytes);
						fos.close();
					}
					running = false;
				}
			}
		}
		socket.close();
	}

	private static void printAckOut(AckPacket ack) {
		long time = System.currentTimeMillis();
		int ackno = ack.getAckno();
		if (ack.getChecksum() == 1) {
			System.out.println("Datagram " + ackno + " was created but was corrupted by the network");
			System.out.println("SENDing ACK " + ackno + " " + time + " ERR");
		} else if (ack.getChecksum() == 2) {
			System.out.println("Datagram " + ackno + " was created but was dropped by the network");
			System.out.println("SENDing ACK " + ackno + " " + time + " DROP");
		} else {
			System.out.println("Datagram " + ackno + " was received successfully");
			System.out.println("SENDing ACK " + ackno + " " + time + " SENT");
		}
	}

	private static void printIncomingPacket(DataPacket packet) {
		long time = System.currentTimeMillis();
		String action = "RECV";
		String condition = "RECV";
		String msg = "Datagram " + packet.getSeqno() + " was received successfully";
		if (!packet.isValid()) {
			msg = "Datagram " + packet.getSeqno() + " was received but with an error";
			condition = "CRPT";
		}
		if (!isExpected(packet)) {
			msg = "Datagram " + packet.getSeqno() + " was received out of order";
			condition = "!Seq";
		}
		if (duplicate(packet)) {
			msg = "Datagram " + packet.getSeqno() + " was received a second time (duplicate)";
			action = "DUPL";
		}
		System.out.println(msg);
		System.out.println(action + " " + time + " " + packet.getSeqno() + " " + condition);
	}

	private static boolean duplicate(DataPacket packet) {
		for (DataPacket dataPacket : allPackets) {
			if (dataPacket.getSeqno() == packet.getSeqno()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isExpected(DataPacket packet) {
		for (int expected : window) {
			if (packet.getSeqno() == expected) {
				return true;
			}
		}
		return false;
	}

	private static void corruptPacket(DataPacket packet) {
		Random rand = new Random();
		if (rand.nextFloat() <= datagramPercentage) {
			packet.corrupt();
		}
	}

	private static boolean inWindow(DataPacket packet) {
		for (int i = 0; i < window.length; i++) {
			if (packet.getSeqno() == window[i]) {
				window[i] = nextPacketSeqno++;
				return true;
			}
		}

		return false;
	}

	private static void initializeWindow() {
		window = new int[sizeOfWindow];
		// fill window with values starting at 1 which is the first expected packet
		for (int i = 0; i < window.length; i++) {
			window[i] = nextPacketSeqno++;
		}
	}

	private static void readArgs(String[] args) {
		defineCommandLineOptions();
		parseCommandLineArguments(args);
		setWindow();
		setDatagram();
		setServer();
		displayHelp();
	}

	private static void defineCommandLineOptions() {
		// window option
		Option windowOption = new Option("w", "window", true, "sliding window size");
		windowOption.setRequired(false);
		options.addOption(windowOption);
		// drop option
		Option dropOption = new Option("d", "datagram", true, "percentage of datagrams to corrupt, delay, or drop");
		dropOption.setRequired(false);
		options.addOption(windowOption);
		Option helpOption = new Option("h", "help", false, "show options");
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

	private static void setWindow() {
		if (cmd.hasOption("window")) {
			// sliding window size specified by user
			sizeOfWindow = Integer.parseInt(cmd.getOptionValue("window"));
		} else {
			// sliding window size set to default
			sizeOfWindow = DEFAULT_WINDOW;
		}
	}

	private static void displayHelp() {
		if (cmd.hasOption("help")) {
			// help asked from the user
			formatter.printHelp(Client.class.getSimpleName(), options);
			System.exit(0);
		} else {
			// help is not asked
		}
	}

	private static void setDatagram() {
		if (cmd.hasOption("datagram")) {
			// datagram percentage specified by user
			datagramPercentage = Double.parseDouble(cmd.getOptionValue("datagram"));
		}
	}

	private static void sendAck(int ackno, DatagramSocket socket, InetAddress hostAddress) throws IOException {
		AckPacket ack = new AckPacket((short) 0, ackno);
		corruptAck(ack);
		DatagramPacket out = new DatagramPacket(ack.toDataSegment(), ack.toDataSegment().length, hostAddress,
				serverPort);
		socket.send(out);

	}

	private static void corruptAck(AckPacket ack) {
		Random rand = new Random();
		if (rand.nextFloat() < datagramPercentage) {
			if (rand.nextFloat() <= .5) {
				ack.corrupt();
				printAckOut(ack);
				ack.validCheckSum();
				return;
			} else {
				ack.drop();
				printAckOut(ack);
				ack.validCheckSum();
				return;
			}
		}
		printAckOut(ack);
	}

	private static void setServer() {
		if (!cmd.getArgList().isEmpty()) {
			// receiver address set by user
			byte[] hostAddress = cmd.getArgList().iterator().next().getBytes();
			try {
				serverAddress = InetAddress.getByAddress(hostAddress);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			// receiver port set by user
			serverPort = Integer.parseInt(cmd.getArgList().iterator().next());
		} else {
			// receiver port set to default
			try {
				serverAddress = InetAddress.getByName("localhost");
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	/**
	 * Gets the bytes.
	 *
	 * @param dataSegments
	 *            the data segments
	 * @return the bytes
	 */
	static byte[] getBytes() {
		int fileLength = payloadSize * allPackets.size();
		byte[] fileBytes = new byte[fileLength];
		int nextByte = 0;
		for (DataPacket packet : allPackets) {
			for (byte b : packet.getData()) {
				fileBytes[nextByte++] = b;
			}
		}
		return fileBytes;
	}

	static byte[] getDataSegment(DatagramSocket socket) throws IOException {
		byte[] dataSegment = new byte[packetSize];
		DatagramPacket datagramPacket = new DatagramPacket(dataSegment, dataSegment.length);
		socket.receive(datagramPacket);
		dataSegment = datagramPacket.getData();
		return dataSegment;
	}
}
