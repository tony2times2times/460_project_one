import java.nio.ByteBuffer;

public class DataPacket extends Packet {
	private static final int OVER_HEAD = 16;
	private int seqno;
	private int totalPackets;
	private byte[] data;

	DataPacket(byte[] dataSegment) {
		int pointer = 0;
		checksum = getShort(dataSegment, pointer);
		pointer += 2;
		length = getShort(dataSegment, pointer);
		pointer += 2;
		ackno = getInt(dataSegment, pointer);
		pointer += 4;
		seqno = getInt(dataSegment, pointer);
		pointer += 4;
		totalPackets = getInt(dataSegment, pointer);
		pointer += 4;
		int dataSize = (dataSegment.length - OVER_HEAD);
		data = new byte[dataSize];
		System.arraycopy(dataSegment, pointer, data, 0, dataSize);

	}

	DataPacket(short checksum, int ackno, int seqno, int totalPackets, byte[] data) {
		this.checksum = checksum;
		this.ackno = ackno;
		this.seqno = seqno;
		this.totalPackets = totalPackets;
		this.data = data;
		this.length = (short) (16 + data.length);
	}

	public int getSeqno() {
		return seqno;
	}

	public byte[] getData() {
		return data;
	}

	public int getTotalPackets() {
		return totalPackets;
	}

	public void corrupt() {
		checksum = 1;
	}

	public byte[] toDataSegment() {
		int pointer = 0;
		byte[] dataSegment = new byte[length];
		byte[] shortArr = new byte[2];
		byte[] intArr = new byte[4];
		
		shortArr = ByteBuffer.allocate(2).putShort(checksum).array();
		System.arraycopy(shortArr, 0, dataSegment, pointer, 2);
		pointer += 2;
		shortArr = ByteBuffer.allocate(2).putShort(length).array();
		System.arraycopy(shortArr, 0, dataSegment, pointer, 2);
		pointer += 2;
		intArr = ByteBuffer.allocate(4).putInt(ackno).array();
		System.arraycopy(intArr, 0, dataSegment, pointer, 4);
		pointer += 4;
		intArr = ByteBuffer.allocate(4).putInt(seqno).array();
		System.arraycopy(intArr, 0, dataSegment, pointer, 4);
		pointer += 4;
		intArr = ByteBuffer.allocate(4).putInt(totalPackets).array();
		System.arraycopy(intArr, 0, dataSegment, pointer, 4);
		pointer += 4;
		System.arraycopy(data, 0, dataSegment, pointer, data.length);
		return dataSegment;
	}

}
