import java.io.*;
import java.net.*;
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
 * The server reads a stream of data (from a file), breaks it into fixed-sized packets 
 * suitable for UDP transport, prepends a control header to the data, and sends each packet
 * to the client.
 */
 
public class Server {

	/** Default Values */
	private static final String DEFAULT_HOSTNAME = "localhost";
	private static final int DEFAULT_PORT = 3000;
	private static final int DEFAULT_PACKET_SIZE = 64;
	private static final int DEFAULT_TIMEOUT = 2000;
	private static final int DEFAULT_WINDOW = 4;
	private static final double DEFAULT_DATAGRAM = 0.25;
	private static final String DEFAULT_FILEPATH = "test.txt";

	/** Logger */
	private static final Logger audit = Logger.getLogger("requests");
	private static final Logger errors = Logger.getLogger("errors");
	
	/** Private members */
	private static int sizeOfPacket;
	private static int timeoutInterval;
	private static int sizeOfWindow;
	private static double datagramPercentage;
	private static int receiverPort;
	private static InetAddress receiverAddress;
	private static String filePath;

	/** Explanatory Variables */
	private static final String OPTION_SIZE_SHORT = "s";
	private static final String OPTION_SIZE_LONG = "size";
	private static final String OPTION_SIZE_DESCRIPTION = "size of packet";
	private static final String OPTION_TIMEOUT_SHORT = "t";
	private static final String OPTION_TIMEOUT_LONG = "timeout";
	private static final String OPTION_TIMEOUT_DESCRIPTION = "timeout interval";
	private static final String OPTION_WINDOW_SHORT = "w";
	private static final String OPTION_WINDOW_LONG = "window";
	private static final String OPTION_WINDOW_DESCRIPTION = "sliding window size";
	private static final String OPTION_DATAGRAM_SHORT = "d";
	private static final String OPTION_DATAGRAM_LONG = "datagram";
	private static final String OPTION_DATAGRAM_DESCRIPTION = "percentage of datagram to corrupt, delay, or drop";
	private static final String OPTION_HELP_SHORT = "h";
	private static final String OPTION_HELP_LONG = "help";
	private static final String OPTION_HELP_DESCRIPTION = "show options";
	private static final String OPTION_FILE_SHORT = "f";
	private static final String OPTION_FILE_LONG = "file";
	private static final String OPTION_FILE_DESCRIPTION = "file path";
	
	/**
	*	Server 
	*	Main
	*/
	public static void main(String args[]) {
		
		/**
		 * Define command line options 
		 **/
		// hold argument options
		Options options = new Options();
		
		// size option
		Option sizeOption = new Option(OPTION_SIZE_SHORT, OPTION_SIZE_LONG, true, OPTION_SIZE_DESCRIPTION);
		sizeOption.setRequired(false);
		options.addOption(sizeOption);
		
		// timeout option
		Option timeoutOption = new Option(OPTION_TIMEOUT_SHORT, OPTION_TIMEOUT_LONG, true, OPTION_TIMEOUT_DESCRIPTION);
		timeoutOption.setRequired(false);
		options.addOption(timeoutOption);
		
		// window option
		Option windowOption = new Option(OPTION_WINDOW_SHORT, OPTION_WINDOW_LONG, true, OPTION_WINDOW_DESCRIPTION);
		windowOption.setRequired(false);
		options.addOption(windowOption);
		
		// datagram option
		Option datagramOption = new Option(OPTION_DATAGRAM_SHORT, OPTION_DATAGRAM_LONG, true, OPTION_DATAGRAM_DESCRIPTION);
		datagramOption.setRequired(false);
		options.addOption(datagramOption);
		
		// file option
		Option fileOption = new Option(OPTION_FILE_SHORT, OPTION_FILE_LONG, true, OPTION_FILE_DESCRIPTION);
		fileOption.setRequired(false);
		options.addOption(fileOption);
		 
		// help option
		Option helpOption = new Option(OPTION_HELP_SHORT, OPTION_HELP_LONG, false, OPTION_HELP_DESCRIPTION);
		helpOption.setRequired(false);
		options.addOption(helpOption);
		
		/**
		 * Parse command line arguments 
		 **/
		// parser for parsing options
		CommandLineParser parser = new DefaultParser();
		// formatter for formatting help
		HelpFormatter formatter = new HelpFormatter();
		// command line to get options from
		CommandLine cmd = null;

		try {
			// parse options into command line
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			// print exception and help then exit
			System.out.println(e.getMessage());
			formatter.printHelp(Server.class.getSimpleName(), options);
			System.exit(1);
		}
		
		/** 
		 * Interrogate command line arguments 
		 **/
		if(cmd.hasOption(OPTION_SIZE_LONG)) {
			// size of packet specified by user
			sizeOfPacket = Integer.parseInt( cmd.getOptionValue(OPTION_SIZE_LONG) );
		} else {
			// size of packet set to default 
			sizeOfPacket = DEFAULT_PACKET_SIZE;
		}
		
		if(cmd.hasOption(OPTION_TIMEOUT_LONG)) {
			// timeout interval specified by user
			timeoutInterval = Integer.parseInt( cmd.getOptionValue(OPTION_TIMEOUT_LONG) );
		} else {
			// timeout interval set to default
			timeoutInterval = DEFAULT_TIMEOUT;
		}
		
		if(cmd.hasOption(OPTION_WINDOW_LONG)) {
			// sliding window size specified by user
			sizeOfWindow = Integer.parseInt( cmd.getOptionValue(OPTION_WINDOW_LONG) );
		} else {
			// sliding window size set to default
			sizeOfWindow = DEFAULT_WINDOW;
		}
		
		if(cmd.hasOption(OPTION_DATAGRAM_LONG)) {
			// datagram percentage specified by user
			datagramPercentage = Double.parseDouble( cmd.getOptionValue(OPTION_DATAGRAM_LONG) );
		} else {
			// datagram percentage set to default
			datagramPercentage = DEFAULT_DATAGRAM;
		}
		
		if(cmd.hasOption(OPTION_FILE_LONG)) {
			// file path specified by user
			filePath = cmd.getOptionValue(OPTION_FILE_LONG);
		} else {
			// file path set to default
			filePath = DEFAULT_FILEPATH;
		}
		
		if( !cmd.getArgList().isEmpty() ) {
			// receiver address set by user
			byte[] hostAddress = cmd.getArgList().iterator().next().getBytes();
			try {
				receiverAddress = InetAddress.getByAddress(hostAddress);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			// receiver port set by user
			receiverPort = Integer.parseInt( cmd.getArgList().iterator().next() );
		} else {
			// receiver port set to default
			try {
				receiverAddress = InetAddress.getByName(DEFAULT_HOSTNAME);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			// receiver address set to default
			receiverPort = DEFAULT_PORT;
		}
			
		/**
		 * Establish Server Socket
		 * Read the file
		 * Divide the file into segments
		 * Send the packets ********** acknowledge packet + timeout need to be implemented
		 */		
		try ( DatagramSocket datagramSocket = new DatagramSocket(receiverPort) ) {
			while (true) {
			 	try {
					// read the specified file
					File file = new File(filePath);
					int fileLength = (int) file.length();
					byte[] data = new byte[fileLength];
					FileInputStream in = new FileInputStream(file);
					in.read(data);
					in.close();
					
					// divide the file into data segments
					ArrayList<byte[]> dataSegments = getDataSegments(data);
					
					// Send the data packets
					for (byte[] dataSegment : dataSegments) {
						DatagramPacket packet = new DatagramPacket( dataSegment, dataSegment.length, receiverAddress, receiverPort );
						datagramSocket.send(packet);
						printPacket(dataSegment);
					}
					
					// close the server socket
					datagramSocket.close();
					// break the loop
					break;
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
		int start = (currentPacket - 1) * PAYLOAD_SIZE + 1;
		int end = (currentPacket - 1) * PAYLOAD_SIZE + PAYLOAD_SIZE;

		audit.info("[packet#" + dataSegment[0] + "]-" + "[" + start + "]-" + "[" + end + "]\n");
	}
}
