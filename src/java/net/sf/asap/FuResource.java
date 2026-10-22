// Generated automatically with "fut". Do not edit.
package net.sf.asap;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;

class FuResource
{
	static byte[] getByteArray(String name, int length)
	{
		InputStream is = FuResource.class.getResourceAsStream(name);
		if (is == null)
			throw new RuntimeException("Missing resource: " + name);
		DataInputStream dis = new DataInputStream(is);
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
