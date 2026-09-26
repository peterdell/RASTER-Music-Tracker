// Generated automatically with "fut". Do not edit.
package net.sf.asap;

final class ASAP6502
{
	private ASAP6502()
	{
	}

	static byte[] getPlayerRoutine(ASAPInfo info)
	{
		switch (info.type) {
		case CMC:
			return FuResource.getByteArray("cmc.obx", 2019);
		case CM3:
			return FuResource.getByteArray("cm3.obx", 2022);
		case CMR:
			return FuResource.getByteArray("cmr.obx", 2019);
		case CMS:
			return FuResource.getByteArray("cms.obx", 2757);
		case DLT:
			return FuResource.getByteArray("dlt.obx", 2125);
		case MPT:
		case MD1:
		case MD2:
			return FuResource.getByteArray("mpt.obx", 2233);
		case RMT:
			return info.getChannels() == 1 ? FuResource.getByteArray("rmt4.obx", 2007) : FuResource.getByteArray("rmt8.obx", 2275);
		case TMC:
			return FuResource.getByteArray("tmc.obx", 2671);
		case TM2:
			return FuResource.getByteArray("tm2.obx", 3698);
		case FC:
			return FuResource.getByteArray("fc.obx", 1220);
		default:
			return null;
		}
	}
}
