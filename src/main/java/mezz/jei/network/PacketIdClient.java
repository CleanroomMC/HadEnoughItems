package mezz.jei.network;

public enum PacketIdClient implements IPacketId {
	CHEAT_PERMISSION,
	CRAFT_UPDATE;

	public static final PacketIdClient[] VALUES = values();
}
