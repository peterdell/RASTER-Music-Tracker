// Generated automatically with "fut". Do not edit.
package net.sf.asap;

class Cpu6502
{
	ASAP asap;
	final byte[] memory = new byte[65536];
	int cycle;
	int pc;
	int a;
	int x;
	int y;
	int s;
	private int nz;
	private int c;
	private int vdi;

	private static final int V_FLAG = 64;

	private static final int D_FLAG = 8;

	private static final int I_FLAG = 4;

	private static final int Z_FLAG = 2;

	final void reset()
	{
		this.cycle = 0;
		this.nz = 0;
		this.c = 0;
		this.vdi = 0;
	}

	private int peek(int addr)
	{
		if ((addr & 63744) == 53248)
			return this.asap.peekHardware(addr);
		else
			return this.memory[addr] & 0xff;
	}

	private void poke(int addr, int data)
	{
		if ((addr & 63744) == 53248)
			this.asap.pokeHardware(addr, data);
		else
			this.memory[addr] = (byte) data;
	}

	private int peekReadModifyWrite(int addr)
	{
		if (addr >> 8 == 210) {
			this.cycle--;
			int data = this.asap.peekHardware(addr);
			this.asap.pokeHardware(addr, data);
			this.cycle++;
			return data;
		}
		return this.memory[addr] & 0xff;
	}

	private int pull()
	{
		int s = (this.s + 1) & 255;
		this.s = s;
		return this.memory[256 + s] & 0xff;
	}

	private void pullFlags()
	{
		int data = pull();
		this.nz = ((data & 128) << 1) + (~data & 2);
		this.c = data & 1;
		this.vdi = data & 76;
	}

	private void push(int data)
	{
		int s = this.s;
		this.memory[256 + s] = (byte) data;
		this.s = (s - 1) & 255;
	}

	final void pushPc()
	{
		push(this.pc >> 8);
		push(this.pc & 255);
	}

	private void pushFlags(int b)
	{
		int nz = this.nz;
		b += ((nz | nz >> 1) & 128) + this.vdi + this.c;
		if ((nz & 255) == 0)
			b += 2;
		push(b);
	}

	private void addWithCarry(int data)
	{
		int a = this.a;
		int vdi = this.vdi;
		int tmp = a + data + this.c;
		this.nz = tmp & 255;
		if ((vdi & 8) == 0) {
			this.vdi = (vdi & 12) + ((~(data ^ a) & (a ^ tmp)) >> 1 & 64);
			this.c = tmp >> 8;
			this.a = this.nz;
		}
		else {
			int al = (a & 15) + (data & 15) + this.c;
			if (al >= 10) {
				tmp += al < 26 ? 6 : -10;
				if (this.nz != 0)
					this.nz = (tmp & 128) + 1;
			}
			this.vdi = (vdi & 12) + ((~(data ^ a) & (a ^ tmp)) >> 1 & 64);
			if (tmp >= 160) {
				this.c = 1;
				this.a = (tmp - 160) & 255;
			}
			else {
				this.c = 0;
				this.a = tmp;
			}
		}
	}

	private void subtractWithCarry(int data)
	{
		int a = this.a;
		int vdi = this.vdi;
		int borrow = this.c - 1;
		int tmp = a - data + borrow;
		int al = (a & 15) - (data & 15) + borrow;
		this.vdi = (vdi & 12) + (((data ^ a) & (a ^ tmp)) >> 1 & 64);
		this.c = tmp >= 0 ? 1 : 0;
		this.nz = this.a = tmp & 255;
		if ((vdi & 8) != 0) {
			if (al < 0)
				this.a += al < -10 ? 10 : -6;
			if (this.c == 0)
				this.a = (this.a - 96) & 255;
		}
	}

	private int arithmeticShiftLeft(int addr)
	{
		int data = peekReadModifyWrite(addr);
		this.c = data >> 7;
		data = data << 1 & 255;
		poke(addr, data);
		return data;
	}

	private int rotateLeft(int addr)
	{
		int data = (peekReadModifyWrite(addr) << 1) + this.c;
		this.c = data >> 8;
		data &= 255;
		poke(addr, data);
		return data;
	}

	private int logicalShiftRight(int addr)
	{
		int data = peekReadModifyWrite(addr);
		this.c = data & 1;
		data >>= 1;
		poke(addr, data);
		return data;
	}

	private int rotateRight(int addr)
	{
		int data = (this.c << 8) + peekReadModifyWrite(addr);
		this.c = data & 1;
		data >>= 1;
		poke(addr, data);
		return data;
	}

	private int decrement(int addr)
	{
		int data = (peekReadModifyWrite(addr) - 1) & 255;
		poke(addr, data);
		return data;
	}

	private int increment(int addr)
	{
		int data = (peekReadModifyWrite(addr) + 1) & 255;
		poke(addr, data);
		return data;
	}

	private void executeIrq(int b)
	{
		pushPc();
		pushFlags(b);
		this.vdi |= 4;
		this.pc = (this.memory[65534] & 0xff) + ((this.memory[65535] & 0xff) << 8);
	}

	private void checkIrq()
	{
		if ((this.vdi & 4) == 0 && this.asap.isIrq()) {
			this.cycle += 7;
			executeIrq(32);
		}
	}

	private void shx(int addr, int data)
	{
		addr += this.memory[this.pc++] & 0xff;
		int hi = this.memory[this.pc++] & 0xff;
		data &= hi + 1;
		if (addr >= 256)
			hi = data - 1;
		addr += hi << 8;
		poke(addr, data);
	}

	/**
	 * Runs 6502 emulation for the specified number of Atari scanlines.
	 * Each scanline is 114 cycles of which 9 is taken by ANTIC for memory refresh.
	 */
	final void doFrame(int cycleLimit)
	{
		while (this.cycle < cycleLimit) {
			if (this.cycle >= this.asap.nextEventCycle) {
				this.asap.handleEvent();
				checkIrq();
			}
			int data = this.memory[this.pc++] & 0xff;
			this.cycle += DO_FRAME_OPCODE_CYCLES[data] & 0xff;
			int addr = 0;
			switch (data) {
			case 0:
				this.pc++;
				executeIrq(48);
				continue;
			case 1:
			case 3:
			case 33:
			case 35:
			case 65:
			case 67:
			case 97:
			case 99:
			case 129:
			case 131:
			case 161:
			case 163:
			case 193:
			case 195:
			case 225:
			case 227:
				addr = ((this.memory[this.pc++] & 0xff) + this.x) & 255;
				addr = (this.memory[addr] & 0xff) + ((this.memory[(addr + 1) & 255] & 0xff) << 8);
				break;
			case 2:
			case 18:
			case 34:
			case 50:
			case 66:
			case 82:
			case 98:
			case 114:
			case 146:
			case 178:
			case 210:
			case 242:
				this.pc--;
				this.cycle = this.asap.nextEventCycle;
				continue;
			case 4:
			case 68:
			case 100:
			case 20:
			case 52:
			case 84:
			case 116:
			case 212:
			case 244:
			case 128:
			case 130:
			case 137:
			case 194:
			case 226:
				this.pc++;
				continue;
			case 5:
			case 6:
			case 7:
			case 36:
			case 37:
			case 38:
			case 39:
			case 69:
			case 70:
			case 71:
			case 101:
			case 102:
			case 103:
			case 132:
			case 133:
			case 134:
			case 135:
			case 164:
			case 165:
			case 166:
			case 167:
			case 196:
			case 197:
			case 198:
			case 199:
			case 228:
			case 229:
			case 230:
			case 231:
				addr = this.memory[this.pc++] & 0xff;
				break;
			case 8:
				pushFlags(48);
				continue;
			case 9:
			case 41:
			case 73:
			case 105:
			case 160:
			case 162:
			case 169:
			case 192:
			case 201:
			case 224:
			case 233:
			case 235:
				addr = this.pc++;
				break;
			case 10:
				this.c = this.a >> 7;
				this.nz = this.a = this.a << 1 & 255;
				continue;
			case 11:
			case 43:
				this.nz = this.a &= this.memory[this.pc++] & 0xff;
				this.c = this.nz >> 7;
				continue;
			case 12:
				this.pc += 2;
				continue;
			case 13:
			case 14:
			case 15:
			case 44:
			case 45:
			case 46:
			case 47:
			case 77:
			case 78:
			case 79:
			case 108:
			case 109:
			case 110:
			case 111:
			case 140:
			case 141:
			case 142:
			case 143:
			case 172:
			case 173:
			case 174:
			case 175:
			case 204:
			case 205:
			case 206:
			case 207:
			case 236:
			case 237:
			case 238:
			case 239:
				addr = this.memory[this.pc++] & 0xff;
				addr += (this.memory[this.pc++] & 0xff) << 8;
				break;
			case 16:
				if (this.nz < 128)
					break;
				this.pc++;
				continue;
			case 17:
			case 49:
			case 81:
			case 113:
			case 177:
			case 179:
			case 209:
			case 241:
				int zp = this.memory[this.pc++] & 0xff;
				addr = (this.memory[zp] & 0xff) + this.y;
				if (addr >= 256)
					this.cycle++;
				addr = (addr + ((this.memory[(zp + 1) & 255] & 0xff) << 8)) & 65535;
				break;
			case 19:
			case 51:
			case 83:
			case 115:
			case 145:
			case 211:
			case 243:
				addr = this.memory[this.pc++] & 0xff;
				addr = ((this.memory[addr] & 0xff) + ((this.memory[(addr + 1) & 255] & 0xff) << 8) + this.y) & 65535;
				break;
			case 21:
			case 22:
			case 23:
			case 53:
			case 54:
			case 55:
			case 85:
			case 86:
			case 87:
			case 117:
			case 118:
			case 119:
			case 148:
			case 149:
			case 180:
			case 181:
			case 213:
			case 214:
			case 215:
			case 245:
			case 246:
			case 247:
				addr = ((this.memory[this.pc++] & 0xff) + this.x) & 255;
				break;
			case 24:
				this.c = 0;
				continue;
			case 25:
			case 57:
			case 89:
			case 121:
			case 185:
			case 187:
			case 190:
			case 191:
			case 217:
			case 249:
				addr = (this.memory[this.pc++] & 0xff) + this.y;
				if (addr >= 256)
					this.cycle++;
				addr = (addr + ((this.memory[this.pc++] & 0xff) << 8)) & 65535;
				break;
			case 27:
			case 59:
			case 91:
			case 123:
			case 153:
			case 219:
			case 251:
				addr = (this.memory[this.pc++] & 0xff) + this.y;
				addr = (addr + ((this.memory[this.pc++] & 0xff) << 8)) & 65535;
				break;
			case 28:
			case 60:
			case 92:
			case 124:
			case 220:
			case 252:
				if ((this.memory[this.pc] & 0xff) + this.x >= 256)
					this.cycle++;
				this.pc += 2;
				continue;
			case 29:
			case 61:
			case 93:
			case 125:
			case 188:
			case 189:
			case 221:
			case 253:
				addr = (this.memory[this.pc++] & 0xff) + this.x;
				if (addr >= 256)
					this.cycle++;
				addr = (addr + ((this.memory[this.pc++] & 0xff) << 8)) & 65535;
				break;
			case 30:
			case 31:
			case 62:
			case 63:
			case 94:
			case 95:
			case 126:
			case 127:
			case 157:
			case 222:
			case 223:
			case 254:
			case 255:
				addr = (this.memory[this.pc++] & 0xff) + this.x;
				addr = (addr + ((this.memory[this.pc++] & 0xff) << 8)) & 65535;
				break;
			case 32:
				addr = this.memory[this.pc++] & 0xff;
				pushPc();
				this.pc = addr + ((this.memory[this.pc] & 0xff) << 8);
				continue;
			case 40:
				pullFlags();
				checkIrq();
				continue;
			case 42:
				this.a = (this.a << 1) + this.c;
				this.c = this.a >> 8;
				this.nz = this.a &= 255;
				continue;
			case 48:
				if (this.nz >= 128)
					break;
				this.pc++;
				continue;
			case 56:
				this.c = 1;
				continue;
			case 64:
				pullFlags();
				this.pc = pull();
				this.pc += pull() << 8;
				checkIrq();
				continue;
			case 72:
				push(this.a);
				continue;
			case 74:
				this.c = this.a & 1;
				this.nz = this.a >>= 1;
				continue;
			case 75:
				this.a &= this.memory[this.pc++] & 0xff;
				this.c = this.a & 1;
				this.nz = this.a >>= 1;
				continue;
			case 76:
				addr = this.memory[this.pc++] & 0xff;
				this.pc = addr + ((this.memory[this.pc] & 0xff) << 8);
				continue;
			case 80:
				if ((this.vdi & 64) == 0)
					break;
				this.pc++;
				continue;
			case 88:
				this.vdi &= 72;
				checkIrq();
				continue;
			case 96:
				this.pc = pull();
				this.pc += (pull() << 8) + 1;
				continue;
			case 104:
				this.nz = this.a = pull();
				continue;
			case 106:
				this.nz = (this.c << 7) + (this.a >> 1);
				this.c = this.a & 1;
				this.a = this.nz;
				continue;
			case 107:
				data = this.a & this.memory[this.pc++] & 0xff;
				this.nz = this.a = (data >> 1) + (this.c << 7);
				this.vdi = (this.vdi & 12) + ((this.a ^ data) & 64);
				if ((this.vdi & 8) == 0)
					this.c = data >> 7;
				else {
					if ((data & 15) >= 5)
						this.a = (this.a & 240) + ((this.a + 6) & 15);
					if (data >= 80) {
						this.a = (this.a + 96) & 255;
						this.c = 1;
					}
					else
						this.c = 0;
				}
				continue;
			case 112:
				if ((this.vdi & 64) != 0)
					break;
				this.pc++;
				continue;
			case 120:
				this.vdi |= 4;
				continue;
			case 136:
				this.nz = this.y = (this.y - 1) & 255;
				continue;
			case 138:
				this.nz = this.a = this.x;
				continue;
			case 139:
				data = this.memory[this.pc++] & 0xff;
				this.a &= (data | 239) & this.x;
				this.nz = this.a & data;
				continue;
			case 144:
				if (this.c == 0)
					break;
				this.pc++;
				continue;
			case 147:
				{
					addr = this.memory[this.pc++] & 0xff;
					int hi = this.memory[(addr + 1) & 255] & 0xff;
					addr = this.memory[addr] & 0xff;
					data = (hi + 1) & this.a & this.x;
					addr += this.y;
					if (addr >= 256)
						hi = data - 1;
					addr += hi << 8;
					poke(addr, data);
				}
				continue;
			case 150:
			case 151:
			case 182:
			case 183:
				addr = ((this.memory[this.pc++] & 0xff) + this.y) & 255;
				break;
			case 152:
				this.nz = this.a = this.y;
				continue;
			case 154:
				this.s = this.x;
				continue;
			case 155:
				this.s = this.a & this.x;
				shx(this.y, this.s);
				continue;
			case 156:
				shx(this.x, this.y);
				continue;
			case 158:
				shx(this.y, this.x);
				continue;
			case 159:
				shx(this.y, this.a & this.x);
				continue;
			case 168:
				this.nz = this.y = this.a;
				continue;
			case 170:
				this.nz = this.x = this.a;
				continue;
			case 171:
				this.nz = this.x = this.a &= this.memory[this.pc++] & 0xff;
				continue;
			case 176:
				if (this.c != 0)
					break;
				this.pc++;
				continue;
			case 184:
				this.vdi &= 12;
				continue;
			case 186:
				this.nz = this.x = this.s;
				continue;
			case 200:
				this.nz = this.y = (this.y + 1) & 255;
				continue;
			case 202:
				this.nz = this.x = (this.x - 1) & 255;
				continue;
			case 203:
				this.nz = this.memory[this.pc++] & 0xff;
				this.x &= this.a;
				this.c = this.x >= this.nz ? 1 : 0;
				this.nz = this.x = (this.x - this.nz) & 255;
				continue;
			case 208:
				if ((this.nz & 255) != 0)
					break;
				this.pc++;
				continue;
			case 216:
				this.vdi &= 68;
				continue;
			case 232:
				this.nz = this.x = (this.x + 1) & 255;
				continue;
			case 234:
			case 26:
			case 58:
			case 90:
			case 122:
			case 218:
			case 250:
				continue;
			case 240:
				if ((this.nz & 255) == 0)
					break;
				this.pc++;
				continue;
			case 248:
				this.vdi |= 8;
				continue;
			default:
				throw new AssertionError();
			}
			switch (data) {
			case 1:
			case 5:
			case 9:
			case 13:
			case 17:
			case 21:
			case 25:
			case 29:
				this.nz = this.a |= peek(addr);
				break;
			case 3:
			case 7:
			case 15:
			case 19:
			case 23:
			case 27:
			case 31:
				this.nz = this.a |= arithmeticShiftLeft(addr);
				break;
			case 6:
			case 14:
			case 22:
			case 30:
				this.nz = arithmeticShiftLeft(addr);
				break;
			case 16:
			case 48:
			case 80:
			case 112:
			case 144:
			case 176:
			case 208:
			case 240:
				addr = (this.memory[this.pc] & 0xff ^ 128) - 128;
				this.pc++;
				addr += this.pc;
				this.cycle += (addr ^ this.pc) >> 8 != 0 ? 2 : 1;
				this.pc = addr;
				break;
			case 33:
			case 37:
			case 41:
			case 45:
			case 49:
			case 53:
			case 57:
			case 61:
				this.nz = this.a &= peek(addr);
				break;
			case 35:
			case 39:
			case 47:
			case 51:
			case 55:
			case 59:
			case 63:
				this.nz = this.a &= rotateLeft(addr);
				break;
			case 36:
			case 44:
				this.nz = peek(addr);
				this.vdi = (this.vdi & 12) + (this.nz & 64);
				this.nz = ((this.nz & 128) << 1) + (this.nz & this.a);
				break;
			case 38:
			case 46:
			case 54:
			case 62:
				this.nz = rotateLeft(addr);
				break;
			case 65:
			case 69:
			case 73:
			case 77:
			case 81:
			case 85:
			case 89:
			case 93:
				this.nz = this.a ^= peek(addr);
				break;
			case 67:
			case 71:
			case 79:
			case 83:
			case 87:
			case 91:
			case 95:
				this.nz = this.a ^= logicalShiftRight(addr);
				break;
			case 70:
			case 78:
			case 86:
			case 94:
				this.nz = logicalShiftRight(addr);
				break;
			case 97:
			case 101:
			case 105:
			case 109:
			case 113:
			case 117:
			case 121:
			case 125:
				addWithCarry(peek(addr));
				break;
			case 99:
			case 103:
			case 111:
			case 115:
			case 119:
			case 123:
			case 127:
				addWithCarry(rotateRight(addr));
				break;
			case 102:
			case 110:
			case 118:
			case 126:
				this.nz = rotateRight(addr);
				break;
			case 108:
				this.pc = this.memory[addr] & 0xff;
				if ((++addr & 255) == 0)
					addr -= 255;
				this.pc += (this.memory[addr] & 0xff) << 8;
				break;
			case 129:
			case 133:
			case 141:
			case 145:
			case 149:
			case 153:
			case 157:
				poke(addr, this.a);
				break;
			case 131:
			case 135:
			case 143:
			case 151:
				poke(addr, this.a & this.x);
				break;
			case 132:
			case 140:
			case 148:
				poke(addr, this.y);
				break;
			case 134:
			case 142:
			case 150:
				poke(addr, this.x);
				break;
			case 160:
			case 164:
			case 172:
			case 180:
			case 188:
				this.nz = this.y = peek(addr);
				break;
			case 161:
			case 165:
			case 169:
			case 173:
			case 177:
			case 181:
			case 185:
			case 189:
				this.nz = this.a = peek(addr);
				break;
			case 162:
			case 166:
			case 174:
			case 182:
			case 190:
				this.nz = this.x = peek(addr);
				break;
			case 163:
			case 167:
			case 175:
			case 179:
			case 183:
			case 191:
				this.nz = this.x = this.a = peek(addr);
				break;
			case 187:
				this.nz = this.x = this.a = this.s &= peek(addr);
				break;
			case 192:
			case 196:
			case 204:
				this.nz = peek(addr);
				this.c = this.y >= this.nz ? 1 : 0;
				this.nz = (this.y - this.nz) & 255;
				break;
			case 193:
			case 197:
			case 201:
			case 205:
			case 209:
			case 213:
			case 217:
			case 221:
				this.nz = peek(addr);
				this.c = this.a >= this.nz ? 1 : 0;
				this.nz = (this.a - this.nz) & 255;
				break;
			case 195:
			case 199:
			case 207:
			case 211:
			case 215:
			case 219:
			case 223:
				data = decrement(addr);
				this.c = this.a >= data ? 1 : 0;
				this.nz = (this.a - data) & 255;
				break;
			case 198:
			case 206:
			case 214:
			case 222:
				this.nz = decrement(addr);
				break;
			case 224:
			case 228:
			case 236:
				this.nz = peek(addr);
				this.c = this.x >= this.nz ? 1 : 0;
				this.nz = (this.x - this.nz) & 255;
				break;
			case 225:
			case 229:
			case 233:
			case 235:
			case 237:
			case 241:
			case 245:
			case 249:
			case 253:
				subtractWithCarry(peek(addr));
				break;
			case 227:
			case 231:
			case 239:
			case 243:
			case 247:
			case 251:
			case 255:
				subtractWithCarry(increment(addr));
				break;
			case 230:
			case 238:
			case 246:
			case 254:
				this.nz = increment(addr);
				break;
			default:
				throw new AssertionError();
			}
		}
	}

	private static final byte[] DO_FRAME_OPCODE_CYCLES = { 7, 6, 2, 8, 3, 3, 5, 5, 3, 2, 2, 2, 4, 4, 6, 6,
		2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
		6, 6, 2, 8, 3, 3, 5, 5, 4, 2, 2, 2, 4, 4, 6, 6,
		2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
		6, 6, 2, 8, 3, 3, 5, 5, 3, 2, 2, 2, 3, 4, 6, 6,
		2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
		6, 6, 2, 8, 3, 3, 5, 5, 4, 2, 2, 2, 5, 4, 6, 6,
		2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
		2, 6, 2, 6, 3, 3, 3, 3, 2, 2, 2, 2, 4, 4, 4, 4,
		2, 6, 2, 6, 4, 4, 4, 4, 2, 5, 2, 5, 5, 5, 5, 5,
		2, 6, 2, 6, 3, 3, 3, 3, 2, 2, 2, 2, 4, 4, 4, 4,
		2, 5, 2, 5, 4, 4, 4, 4, 2, 4, 2, 4, 4, 4, 4, 4,
		2, 6, 2, 8, 3, 3, 5, 5, 2, 2, 2, 2, 4, 4, 6, 6,
		2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7,
		2, 6, 2, 8, 3, 3, 5, 5, 2, 2, 2, 2, 4, 4, 6, 6,
		2, 5, 2, 8, 4, 4, 6, 6, 2, 4, 2, 7, 4, 4, 7, 7 };
}
