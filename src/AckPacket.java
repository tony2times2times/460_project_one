import java.nio.ByteBuffer;

public class AckPacket {

	private short checksum;
	private short length = 8;
	private int ackno;
	
	AckPacket(short checksum, int ackno){
		this.checksum = checksum;
		this.ackno = ackno;

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
	
	public byte[] toBytes(){
		int nextByte = 0;
		byte[] dataSegment = new byte[length];		
		byte[] shortArr = ByteBuffer.allocate(2).putShort(checksum).array();
		System.arraycopy(shortArr, 0, dataSegment, nextByte, 2);
		nextByte += 2;		
		shortArr = ByteBuffer.allocate(2).putShort(length).array();
		System.arraycopy(shortArr, 0, dataSegment, nextByte, 2);
		nextByte += 2;		
		byte[] intArr = ByteBuffer.allocate(4).putInt(ackno).array();
		System.arraycopy(intArr, 0, dataSegment, nextByte, 4);
		return dataSegment;
	}
}
