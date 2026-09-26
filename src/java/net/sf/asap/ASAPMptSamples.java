// Generated automatically with "fut". Do not edit.
package net.sf.asap;

class ASAPMptSamples
{

	private static final int MIN_LENGTH = 288;

	private static final int MAX_LENGTH = 12320;
	final byte[] content = new byte[12320];
	int contentLength;
	boolean is15kHz;

	private boolean doLoad(ASAPFileLoader loader, String moduleFilename, int moduleFilenameLength, int extNumber)
	{
		String extInitial = moduleFilename.charAt(moduleFilenameLength - 3) == 'M' ? "D" : "d";
		String filename = String.format("%s%s%d", moduleFilename.substring(0, moduleFilenameLength - 3), extInitial, extNumber);
		this.contentLength = loader.load(filename, this.content, 12320);
		if (this.contentLength < 288)
			return false;
		int end = -1;
		for (int i = 0; i < 16; i++) {
			int start = this.content[i] & 0xff;
			if (start == 0) {
				for (; i < 16; i++) {
					if (this.content[i] != 0 || this.content[16 + i] != 0)
						return false;
				}
				break;
			}
			if (i > 0 && start != end)
				return false;
			end = this.content[16 + i] & 0xff;
			if (end <= start)
				return false;
		}
		return this.contentLength >= ((end - (this.content[0] & 0xff)) << 8) + 32;
	}

	public final boolean load(ASAPFileLoader loader, String moduleFilename)
	{
		int moduleFilenameLength = moduleFilename.length();
		if (moduleFilename.charAt(moduleFilenameLength - 1) == '1' && doLoad(loader, moduleFilename, moduleFilenameLength, 15)) {
			this.is15kHz = true;
			return true;
		}
		if (doLoad(loader, moduleFilename, moduleFilenameLength, 8)) {
			this.is15kHz = false;
			return true;
		}
		return false;
	}

	public final void relocate(int music, int musicLast)
	{
		int start = ((this.content[0] & 0xff) << 8) - 32;
		if ((musicLast < start || music >= start + this.contentLength) && start >= 3840)
			return;
		int h = 4064 + this.contentLength <= music ? 16 : (53280 - this.contentLength) >> 8;
		h -= this.content[0] & 0xff;
		for (int i = 0; i < 32; i++) {
			if (this.content[i] != 0)
				this.content[i] += h;
		}
	}
}
