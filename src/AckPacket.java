import java.nio.ByteBuffer;

public class AckPacket extends Packet{

	
	
	AckPacket(byte[] dataSegment){
		int pointer = 0;
		short checksum = getShort(dataSegment, pointer);
		pointer += 2;
		short length = getShort(dataSegment, pointer);
		pointer += 2;
		int ackno = getInt(dataSegment, pointer);
		//all AckPackets must have a length of 8
		if (length == 8) {
			new AckPacket(checksum, ackno);
		}
	}
	
	AckPacket(short checksum, int ackno){
		this.checksum = checksum;
		this.length = 8;
		this.ackno = ackno;
	}	
		
	public byte[] toDataSegment(){
		int pointer = 0;
		byte[] dataSegment = new byte[length];		
		byte[] shortArr = ByteBuffer.allocate(2).putShort(checksum).array();
		System.arraycopy(shortArr, 0, dataSegment, pointer, 2);
		pointer += 2;		
		shortArr = ByteBuffer.allocate(2).putShort(length).array();
		System.arraycopy(shortArr, 0, dataSegment, pointer, 2);
		pointer += 2;		
		byte[] intArr = ByteBuffer.allocate(4).putInt(ackno).array();
		System.arraycopy(intArr, 0, dataSegment, pointer, 4);
		return dataSegment;
	}
}
