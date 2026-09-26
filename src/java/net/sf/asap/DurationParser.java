// Generated automatically with "fut". Do not edit.
package net.sf.asap;

class DurationParser
{
	private String source;
	private int position;
	private int length;

	private int parseDigit(int max) throws ASAPFormatException
	{
		if (this.position >= this.length)
			throw new ASAPFormatException("Invalid duration");
		int digit = this.source.charAt(this.position++) - '0';
		if (digit < 0 || digit > max)
			throw new ASAPFormatException("Invalid duration");
		return digit;
	}

	final int parse(String s) throws ASAPFormatException
	{
		this.source = s;
		this.position = 0;
		this.length = s.length();
		int result = parseDigit(9);
		int digit;
		if (this.position < this.length) {
			digit = s.charAt(this.position) - '0';
			if (digit >= 0 && digit <= 9) {
				this.position++;
				result = result * 10 + digit;
			}
			if (this.position < this.length && s.charAt(this.position) == ':') {
				this.position++;
				digit = parseDigit(5);
				result = result * 60 + digit * 10;
				digit = parseDigit(9);
				result += digit;
			}
		}
		result *= 1000;
		if (this.position >= this.length)
			return result;
		if (s.charAt(this.position++) != '.')
			throw new ASAPFormatException("Invalid duration");
		digit = parseDigit(9);
		result += digit * 100;
		if (this.position >= this.length)
			return result;
		digit = parseDigit(9);
		result += digit * 10;
		if (this.position >= this.length)
			return result;
		digit = parseDigit(9);
		result += digit;
		return result;
	}
}
