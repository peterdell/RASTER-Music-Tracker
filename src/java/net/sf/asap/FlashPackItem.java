// Generated automatically with "fut". Do not edit.
package net.sf.asap;

class FlashPackItem
{
	FlashPackItemType type;
	int value;

	final int writeValueTo(byte[] buffer, int index)
	{
		switch (this.type) {
		case LITERAL:
			buffer[index] = (byte) this.value;
			return 1;
		case COPY_TWO_BYTES:
			buffer[index] = (byte) ((128 - this.value) << 1);
			return 1;
		case COPY_THREE_BYTES:
			buffer[index] = (byte) (((128 - this.value) << 1) + 1);
			return 1;
		case COPY_MANY_BYTES:
			buffer[index] = 1;
			buffer[index + 1] = (byte) this.value;
			return 2;
		case SET_ADDRESS:
			int value = this.value - 128;
			buffer[index] = 0;
			buffer[index + 1] = (byte) value;
			buffer[index + 2] = (byte) (value >> 8);
			return 3;
		default:
			buffer[index] = 1;
			buffer[index + 1] = 0;
			return 2;
		}
	}
}
