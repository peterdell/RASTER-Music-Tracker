// Generated automatically with "fut". Do not edit.
package net.sf.asap;
import java.io.DataInputStream;
import java.io.IOException;

class FuResource
{
	static byte[] getByteArray(String name, int length)
	{
		DataInputStream dis = new DataInputStream(FuResource.class.getResourceAsStream(name));
		byte[] result = new byte[length];
		try {
			try {
				dis.readFully(result);
			}
			finally {
				dis.close();
			}
		}
		catch (IOException e) {
			throw new RuntimeException();
		}
		return result;
	}
}
