package vg.civcraft.mc.bettershards;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import vg.civcraft.mc.bettershards.database.DatabaseManager;
import vg.civcraft.mc.bettershards.events.PlayerChangeServerReason;
import vg.civcraft.mc.bettershards.external.MercuryManager;
import vg.civcraft.mc.bettershards.misc.BedLocation;
import vg.civcraft.mc.bettershards.misc.PlayerStillDeadException;
import vg.civcraft.mc.bettershards.misc.TeleportInfo;
import vg.civcraft.mc.bettershards.portal.Portal;

public class BetterShardsAPI {

	private static BetterShardsPlugin plugin;
	private static MercuryManager mercManager;
	private static DatabaseManager db;
	
	public BetterShardsAPI() {
		plugin = BetterShardsPlugin.getInstance();
		db = plugin.getDatabaseManager();
		mercManager = BetterShardsPlugin.getMercuryManager();
	}
	
	/**
	 * Teleports a player to a different shard.
	 * @param p The Player that you wish to connect.
	 * @param serverName The name of the server that you wish the player to be sent to.
	 * @param reason The reason to be identified by the PlayerChangeServerEvent triggered by this method.
	 * @throws PlayerStillDeadException If the player is dead than this exception is thrown. There are issues with trying
	 * to teleport a dead player.  If calling from PlayerRespawnEvent just schedule a sync method to occur after the event.
	 */
	public static boolean connectPlayer(Player p, String serverName, PlayerChangeServerReason reason) throws PlayerStillDeadException {
		return plugin.teleportPlayerToServer(p, serverName, reason);
	}
	
	/**
	 * Teleports a player to a different shard.
	 * @param p The UUID of the Player that you wish to connect.
	 * @param serverName The name of the server that you wish the player to be sent to.
	 * @param reason The reason to be identified by the PlayerChangeServerEvent triggered by this method.
	 * @throws PlayerStillDeadException If the player is dead than this exception is thrown. There are issues with trying
	 * to teleport a dead player.  If calling from PlayerRespawnEvent just schedule a sync method to occur after the event.
	 */
	public static boolean connectPlayer(UUID p, String serverName, PlayerChangeServerReason reason) throws PlayerStillDeadException {
		return plugin.teleportPlayerToServer(p, serverName, reason);
	}
	
	/**
	 * Teleports the specified player to the current server.
	 * @param uuid The name of said player.
	 */
	public static void requestPlayerTeleport(String name) {
		plugin.teleportOtherServerPlayer(name);
	}
	
	/**
	 * Teleports a player to a different shard.
	 * @param p The Player that you wish to connect.
	 * @param portal The portal that the player should be teleported to.
	 * @param reason The reason to be identified by the PlayerChangeServerEvent triggered by this method.
	 * @param data Additional data provided for the teleport
	 * @throws PlayerStillDeadException If the player is dead than this exception is thrown. There are issues with trying
	 * to teleport a dead player.  If calling from PlayerRespawnEvent just schedule a sync method to occur after the event.
	 */
	public static boolean connectPlayer(Player p, Portal portal, PlayerChangeServerReason reason, Object ... data) throws PlayerStillDeadException {
		if (plugin.teleportPlayerToServer(p, portal.getServerName(), reason)) {
			mercManager.teleportPlayer(p.getUniqueId(), portal, data); // We want to do this after because we don't know if a player was teleported yet.
			return true;
		}
		return false;
	}

	public static String getServerName() {
		return BetterShardsPlugin.getCurrentServerName();
	}
	
	public static PortalsManager getPortalsManager() {
		return BetterShardsPlugin.getInstance().getPortalManager();
	}
	
	/**
	 * Checks if a player has a bed.
	 * @param uuid
	 * @return Returns true if player has one, false otherwise.
	 */
	public static boolean hasBed(UUID uuid) {
		return plugin.getBed(uuid) != null;
	}
	
	public static BedLocation getBedLocation(UUID uuid) {
		return plugin.getBed(uuid);
	}
	
	/**
	 * Adds the BedLocation to both the db and to localcaching as well as sending it
	 * to other servers.  This method will also remove any prexisting beds.
	 * @param uuid The uuid of the player getting the bed.
	 * @param bed The BedLocation object.
	 */
	public static void addBedLocation(UUID uuid, BedLocation bed) {
		removeBedLocation(bed);
		plugin.addBedLocation(uuid, bed);
		db.addBedLocation(bed);
		mercManager.sendBedLocation(bed);
	}
	
	public static void removeBedLocation(BedLocation bed) {
		plugin.removeBed(bed.getUUID());
		db.removeBed(bed.getUUID());
		mercManager.removeBedLocation(bed);
	}
	
	/**
	 * Sends the info to servers that a player needs to be teleported.
	 * @param server The server to teleport the player to.
	 * @param uuid The uuid of the player to teleport.
	 * @param info Create a TeleportInfo object to pass.
	 * world can be either the world name or world uuid.
	 */
	public static void teleportPlayer(String server, UUID uuid, TeleportInfo info) {
		mercManager.teleportPlayer(server, uuid, info);
	}
	
	/**
	 * RandomSpawns a player on the server
	 * @param server the server to randomspawn the player on
	 * @param player the player to randomspawn
	 */	
	public static void randomSpawnPlayer(String server, UUID player) {
		try {
			connectPlayer(player, server, PlayerChangeServerReason.RANDOMSPAWN);
		} catch (PlayerStillDeadException e) {
			e.printStackTrace();
		}
		mercManager.notifyRandomSpawn(server, player);
	}
}
