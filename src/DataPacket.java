
public class DataPacket {

	private short checksum;
	private short length;
	private int ackno;
	private int seqno;
	private int totalPackets;
	private byte[] data;
	
	DataPacket(short checksum, short length, int ackno, int seqno, int totalPackets, byte[] data){
		this.checksum = checksum;
		this.length = length;
		this.ackno = ackno;
		this.seqno = seqno;
		this.totalPackets = totalPackets; 
		this.data = data;
	}
	
	public boolean isValid () {
		if ((length > 0) && (checksum == 0)) {
			return true;
		}else {
			return false;
		}
	}

	public short getChecksum() {
		return checksum;
	}

	public short getLength() {
		return length;
	}

	public int getAckno() {
		return ackno;
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
}
