import java.nio.ByteBuffer;

public class Packet {
	protected short checksum;
	protected short length;
	protected int ackno;

	protected static short getShort(byte[] dataSegment, int pointer) {
		byte[] shortArray = new byte[2];
		System.arraycopy(dataSegment, pointer, shortArray, 0, 2);
		ByteBuffer wrappedNum = ByteBuffer.wrap(shortArray);
		return wrappedNum.getShort();
	}

	protected static int getInt(byte[] dataSegment, int pointer) {
		byte[] shortArray = new byte[4];
		System.arraycopy(dataSegment, pointer, shortArray, 0, 4);
		ByteBuffer wrappedNum = ByteBuffer.wrap(shortArray);
		return wrappedNum.getInt();
	}

	public boolean isValid() {
		if (checksum == 0) {
			System.out.println("valid");
			return true;
		} else {
			System.out.println("invalid");
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
}
