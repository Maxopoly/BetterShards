package vg.civcraft.mc.bettershards.database;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import vg.civcraft.mc.bettershards.BetterShardsPlugin;
import vg.civcraft.mc.bettershards.misc.BedLocation;
import vg.civcraft.mc.bettershards.misc.CustomWorldNBTStorage;
import vg.civcraft.mc.bettershards.misc.InventoryIdentifier;
import vg.civcraft.mc.bettershards.misc.LocationWrapper;
import vg.civcraft.mc.bettershards.misc.TeleportInfo;
import vg.civcraft.mc.bettershards.portal.Portal;
import vg.civcraft.mc.bettershards.portal.portals.CircularPortal;
import vg.civcraft.mc.bettershards.portal.portals.CuboidPortal;
import vg.civcraft.mc.bettershards.portal.portals.WorldBorderPortal;
import vg.civcraft.mc.civmodcore.Config;
import vg.civcraft.mc.civmodcore.annotations.CivConfig;
import vg.civcraft.mc.civmodcore.annotations.CivConfigType;
import vg.civcraft.mc.civmodcore.annotations.CivConfigs;
import vg.civcraft.mc.mercury.MercuryAPI;

public class DatabaseManager{

	private BetterShardsPlugin plugin = BetterShardsPlugin.getInstance();
	private Config config;
	private Database db;
	
	private Map<UUID, ByteArrayInputStream> invCache = new ConcurrentHashMap<UUID, ByteArrayInputStream>();
	
	private String addPlayerData, getPlayerData, removePlayerData;
	private String addPortalLoc, getPortalLocByWorld, getPortalLoc, removePortalLoc;
	private String addPortalData, getPortalData, removePortalData, updatePortalData;
	private String addExclude, getAllExclude, removeExclude;
	private String addBedLocation, getAllBedLocation, removeBedLocation;
	private String version, updateVersion;
	
	public DatabaseManager(){
		config = plugin.GetConfig();
		if (!isValidConnection())
			return;
		loadPreparedStatements();
		executeDatabaseStatements();
	}
	
	@CivConfigs({
		@CivConfig(name = "mysql.host", def = "localhost", type = CivConfigType.String),
		@CivConfig(name = "mysql.port", def = "3306", type = CivConfigType.Int),
		@CivConfig(name = "mysql.username", type = CivConfigType.String),
		@CivConfig(name = "mysql.password", type = CivConfigType.String),
		@CivConfig(name = "mysql.dbname", def = "BetterShardsDB", type = CivConfigType.String)
	})
	public boolean isValidConnection(){
		String username = config.get("mysql.username").getString();
		String host = config.get("mysql.host").getString();
		int port = config.get("mysql.port").getInt();
		String password = config.get("mysql.password").getString();
		String dbname = config.get("mysql.dbname").getString();
		db = new Database(host, port, dbname, username, password, plugin.getLogger());
		return db.connect();
	}
	
	private void executeDatabaseStatements() {
		db.execute("create table if not exists createPlayerData("
				+ "uuid varchar(36) not null,"
				+ "entity blob,"
				+ "server int not null,"
				+ "primary key (uuid, server));");
		db.execute("create table if not exists createPortalDataTable("
				+ "id varchar(255) not null,"
				+ "server_name varchar(255) not null,"
				+ "portal_type int not null,"
				+ "partner_id varchar(255),"
				+ "primary key(id));");
		db.execute("create table if not exists createPortalLocData("
				+ "x1 int not null,"
				+ "y1 int not null,"
				+ "z1 int not null,"
				+ "x2 int not null,"
				+ "y2 int not null,"
				+ "z2 int not null,"
				+ "world varchar(255) not null,"
				+ "id varchar(255) not null,"
				+ "primary key loc_id (x1, y1, z1, x2, y2, z2, world, id));");
		db.execute("create table if not exists excludedServers("
				+ "name varchar(20) not null,"
				+ "primary key name_id(name));");
		db.execute("create table if not exists player_beds("
				+ "uuid varchar(36) not null,"
				+ "server varchar(36) not null,"
				+ "world_name varchar(36) not null,"
				+ "x int not null,"
				+ "y int not null,"
				+ "z int not null,"
				+ "primary key bed_id(uuid));");
		db.execute("create table if not exists bettershards_version("
				+ "db_version int not null,"
				+ "update_time varchar(24));");
		int ver = checkVersion();
		if (ver == 0) {
			BetterShardsPlugin.getInstance().getLogger().info("Update to version 2 of the BetterShards.");
			db.execute("alter table createPlayerData add config_sect text;");
			ver = updateVersion(ver);
		}
	}
	
	private int checkVersion() {
		PreparedStatement version = db.prepareStatement(this.version);
		try {
			ResultSet set = version.executeQuery();
			if (!set.next())
				return 0;
			return set.getInt("db_version");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
	
	private int updateVersion(int version) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		PreparedStatement updateVersion = db.prepareStatement(this.updateVersion);
		try {
			updateVersion.setInt(1, version+ 1);
			updateVersion.setString(2, sdf.format(new Date()));
			updateVersion.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ++version;
	}
	
	public boolean isConnected() {
		if (!db.isConnected())
			db.connect();
		return db.isConnected();
	}
	
	private void loadPreparedStatements(){
		addPlayerData = "insert into createPlayerData(uuid, entity, server, config_sect) values(?,?,?,?);";
		getPlayerData = "select * from createPlayerData where uuid = ? and server = ?;";
		removePlayerData = "delete from createPlayerData where uuid = ? and server = ?;";
		
		addPortalLoc = "insert into createPortalLocData(x1, y1, z1, x2, y2, z2, world, id)"
				+ "values (?,?,?,?,?,?,?,?);";
		getPortalLocByWorld = "select * from createPortalLocData where world = ?;";
		getPortalLoc = "select * from createPortalLocData where id = ?;";
		removePortalLoc = "delete from createPortalDataTable where id = ?;";
		
		addPortalData = "insert into createPortalDataTable(id, server_name, portal_type, partner_id)"
				+ "values(?,?,?,?);";
		getPortalData = "select * from createPortalDataTable where id = ?;";
		removePortalData = "delete from createPortalLocData where id = ?;";
		updatePortalData = "update createPortalDataTable set partner_id = ? where id = ?;";
		
		addExclude = "insert ignore into excludedServers(name) values(?);";
		removeExclude = "delete from excludedServers where name = ?;";
		getAllExclude = "select * from excludedServers;";
		
		addBedLocation = "insert into player_beds (uuid, server, world_name, "
				+ "x, y, z) values (?,?,?,?,?,?)";
		getAllBedLocation = "select * from player_beds;";
		removeBedLocation = "delete from player_beds where uuid = ?;";
		
		version = "select max(db_version) as db_version from bettershards_version;";
		updateVersion = "insert into bettershards_version (db_version, update_time) values (?,?);";
	}
	
	/**
	 * Adds a portal instance to the database. Should be called only when
	 * initially creating a Portal Object.
	 */
	public void addPortal(Portal portal){
		isConnected();
		PreparedStatement addPortalLoc = db.prepareStatement(this.addPortalLoc);
		try {
			if (portal instanceof CuboidPortal){
				CuboidPortal p = (CuboidPortal) portal;
				Location first = p.getFirst();
				Location second = p.getSecond();
				addPortalLoc.setInt(1, first.getBlockX());
				addPortalLoc.setInt(2, first.getBlockY());
				addPortalLoc.setInt(3, first.getBlockZ());
				addPortalLoc.setInt(4, second.getBlockX());
				addPortalLoc.setInt(5, second.getBlockY());
				addPortalLoc.setInt(6, second.getBlockZ());
				addPortalLoc.setString(7, first.getWorld().getName());
				addPortalLoc.setString(8, p.getName());
			}
			else if (portal instanceof WorldBorderPortal) {
				WorldBorderPortal p = (WorldBorderPortal) portal;
				LocationWrapper firstW = p.getFirst();
				LocationWrapper secondW = p.getSecond();
				Location first = firstW.getFakeLocation();
				Location second = secondW.getFakeLocation();
				addPortalLoc.setInt(1, first.getBlockX());
				addPortalLoc.setInt(2, first.getBlockY());
				addPortalLoc.setInt(3, first.getBlockZ());
				addPortalLoc.setInt(4, second.getBlockX());
				addPortalLoc.setInt(5, second.getBlockY());
				addPortalLoc.setInt(6, second.getBlockZ());
				addPortalLoc.setString(7, firstW.getActualWorld());
				addPortalLoc.setString(8, p.getName());
			}
			else if (portal instanceof CircularPortal) {
				CircularPortal p = (CircularPortal) portal;
				Location first = p.getFirst();
				Location second = p.getSecond();
				addPortalLoc.setInt(1, first.getBlockX());
				addPortalLoc.setInt(2, first.getBlockY());
				addPortalLoc.setInt(3, first.getBlockZ());
				addPortalLoc.setInt(4, second.getBlockX());
				addPortalLoc.setInt(5, second.getBlockY());
				addPortalLoc.setInt(6, second.getBlockZ());
				addPortalLoc.setString(7, first.getWorld().getName());
				addPortalLoc.setString(8, p.getName());
			}
			addPortalLoc.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				addPortalLoc.close();
			} catch (Exception ex) {}
		}
	}
	
	/**
	 * This method is called internally to remove the player from the cache.
	 * Minecraft executes the code to save the player so nothing needs to be
	 * done from this plugin's point of view.
	 * @param uuid The uuid of the player
	 */
	public void playerQuitServer(UUID uuid) {
		invCache.remove(uuid);
	}
	
	private String serverName = MercuryAPI.serverName();
	public void addPortalData(Portal portal, Portal connection){
		isConnected();
		PreparedStatement addPortalData = db.prepareStatement(this.addPortalData);
		try {
			addPortalData.setString(1, portal.getName());
			addPortalData.setString(2, serverName);
			addPortalData.setInt(3, portal.specialId);
			String name = null;
			if (connection != null)
				name = connection.getName();
			addPortalData.setString(4, name);
			addPortalData.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				addPortalData.close();
			} catch (Exception ex) {}
		}
	}
	
	public void savePlayerData(UUID uuid, ByteArrayOutputStream output, InventoryIdentifier id, 
			ConfigurationSection section) {
		isConnected();
		invCache.remove(uuid); // So if it is loaded again it is recaught.
		PreparedStatement addPlayerData = db.prepareStatement(this.addPlayerData);
		removePlayerData(uuid, id); // So player data won't throw mysql error.
		try {
			addPlayerData.setString(1, uuid.toString());
			addPlayerData.setBytes(2, output.toByteArray());
			addPlayerData.setInt(3, id.ordinal());
			YamlConfiguration yaml = (YamlConfiguration) section;
			addPlayerData.setString(4, yaml != null ? yaml.saveToString() : null);
			addPlayerData.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				addPlayerData.close();
			} catch (Exception ex) {}
		}
	}
	
	public ByteArrayInputStream loadPlayerData(UUID uuid, InventoryIdentifier id){
		isConnected();
		// Here we had it caches before hand so no need to load it again.
		if (invCache.containsKey(uuid))
			return invCache.get(uuid);
		PreparedStatement getPlayerData = db.prepareStatement(this.getPlayerData);
		try {
			getPlayerData.setString(1, uuid.toString());
			getPlayerData.setInt(2, id.ordinal());
			ResultSet set = getPlayerData.executeQuery();
			if (!set.next())
				return new ByteArrayInputStream(new byte[0]);
			YamlConfiguration sect = new YamlConfiguration();
			String sectString = set.getString("config_sect");
			if (sectString != null)
				sect.loadFromString(sectString);
			CustomWorldNBTStorage.getWorldNBTStorage().loadConfigurationSectionForPlayer(uuid, sect);
			return new ByteArrayInputStream(set.getBytes("entity"));			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				getPlayerData.close();
			} catch (Exception ex) {}
		}
		return new ByteArrayInputStream(new byte[0]);
	}
	
	/**
	 * Can only be from worlds that are valid on this server.
	 * This is to prevent possible NullPointExceptions from trying to 
	 * get portals from Worlds that are not present on this server.
	 */
	public List<Portal> getAllPortalsByWorld(World[] worlds){
		isConnected();
		List<Portal> portals = new ArrayList<Portal>();
		for (World w: worlds){
			String world = w.getName();
			PreparedStatement getPortalLocation = db.prepareStatement(getPortalLocByWorld);
			try {
				getPortalLocation.setString(1, world);
				ResultSet set = getPortalLocation.executeQuery();
				while (set.next()) {
					int x1 = set.getInt("x1");
					int y1 = set.getInt("y1");
					int z1 = set.getInt("z1");
					int x2 = set.getInt("x2");
					int y2 = set.getInt("y2");
					int z2 = set.getInt("z2");
					String id = set.getString("id");

					LocationWrapper first = new LocationWrapper(new Location(w, x1, y1, z1));
					LocationWrapper second = new LocationWrapper(new Location(w, x2, y2, z2));
					Portal p = getPortalData(id, first, second);
					portals.add(p);
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					if (getPortalLocation != null) {
						getPortalLocation.close();
					}
				} catch (Exception ex) {}
			}
		}
		return portals;
	}

	public Portal getPortal(String name) {
		isConnected();
		PreparedStatement getPortalData = db.prepareStatement(this.getPortalLoc);
		try {
			getPortalData.setString(1, name);
			ResultSet set = getPortalData.executeQuery();
			if (!set.next())
				return null;
			int x1 = set.getInt("x1");
			int y1 = set.getInt("y1");
			int z1 = set.getInt("z1");
			int x2 = set.getInt("x2");
			int y2 = set.getInt("y2");
			int z2 = set.getInt("z2");
			String world = set.getString("world");
			World w = Bukkit.getWorld(world);
			LocationWrapper first = null, second = null;
			
			// If the World object does not equal null then we know that it is on this server.
			if (w != null) {
				first = new LocationWrapper(new Location(w, x1, y1, z1));
				second = new LocationWrapper(new Location(w, x2, y2, z2));
			}
			else {
				first = new LocationWrapper(world, x1, y1, z1);
				second = new LocationWrapper(world, x2, y2, z2);
			}
			
			return getPortalData(name, first, second);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				getPortalData.close();
			} catch (Exception ex) {}
		}
		return null;
	}
	
	private Portal getPortalData(String name, LocationWrapper first, LocationWrapper second) {
		isConnected();
		PreparedStatement getPortalData = db.prepareStatement(this.getPortalData);
		try {
			getPortalData.setString(1, name);
			ResultSet set = getPortalData.executeQuery();
			if (!set.next())
				return null;
			int specialId = set.getInt("portal_type"); // determine the type of portal.
			String serverName = set.getString("server_name");
			String partner = set.getString("partner_id");
			boolean currentServer = serverName.equals(MercuryAPI.serverName());
			switch (specialId) {
			case 0:
				CuboidPortal p = new CuboidPortal(name, first.getFakeLocation(), second.getFakeLocation(), partner, currentServer);
				p.setServerName(serverName);
				return p;
			case 1:
				WorldBorderPortal wb = new WorldBorderPortal(name, partner, currentServer, first, second);
				wb.setServerName(serverName);
				return wb;
			case 2:
				CircularPortal cp = new CircularPortal(name, partner, currentServer, first.getFakeLocation(), second.getFakeLocation());
				cp.setServerName(serverName);
				return cp;
			default:
				return null;
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				getPortalData.close();
			} catch (Exception ex) {}
		}
		return null;
	}

	public void removePlayerData(UUID uuid, InventoryIdentifier id) {
		isConnected();
		PreparedStatement removePlayerData = db.prepareStatement(this.removePlayerData);
		try {
			removePlayerData.setString(1, uuid.toString());
			removePlayerData.setInt(2, id.ordinal());
			removePlayerData.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				removePlayerData.close();
			} catch (Exception ex) {}
		}
	}

	public void removePortalLoc(Portal p) {
		isConnected();
		PreparedStatement removePortalLoc = db.prepareStatement(this.removePortalLoc);
		try {
			removePortalLoc.setString(1, p.getName());
			removePortalLoc.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				removePortalLoc.close();
			} catch (Exception ex) {}
		}
	}

	public void removePortalData(Portal p) {
		isConnected();
		PreparedStatement removePortalData = db.prepareStatement(this.removePortalData);
		try {
			removePortalData.setString(1, p.getName());
			removePortalData.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				removePortalData.close();
			} catch (Exception ex) {}
		}
	}

	public void updatePortalData(Portal p) {
		isConnected();
		PreparedStatement updatePortalData = db.prepareStatement(this.updatePortalData);
		try {
			String partner = null;
			if (p.getPartnerPortal() != null)
				partner = p.getPartnerPortal().getName();
			updatePortalData.setString(1, partner);
			updatePortalData.setString(2, p.getName());
			updatePortalData.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				updatePortalData.close();
			} catch (Exception ex) {}
		}
	}

	public void addExclude(String server) {
		isConnected();
		PreparedStatement addExclude = db.prepareStatement(this.addExclude);
		try {
			addExclude.setString(1, server);
			addExclude.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				addExclude.close();
			} catch (Exception ex) {}
		}
	}

	public List <String> getAllExclude() {
		isConnected();
		PreparedStatement getAllExclude = db.prepareStatement(this.getAllExclude);
		List <String> result = new LinkedList <String> ();
		try {
			ResultSet set = getAllExclude.executeQuery();
			while (set.next()) {
				result.add(set.getString("name"));
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				getAllExclude.close();
			} catch (Exception ex) {}
		}
		return result;
	}

	public void removeExclude(String server) {
		isConnected();
		PreparedStatement removeExclude = db.prepareStatement(this.removeExclude);
		try {
			removeExclude.setString(1, server);
			removeExclude.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				removeExclude.close();
			} catch (Exception ex) {}
		}
	}

	public void addBedLocation(BedLocation bed) {
		isConnected();
		PreparedStatement addBedLocation = db.prepareStatement(this.addBedLocation);
		TeleportInfo info = bed.getTeleportInfo();
		try {
			addBedLocation.setString(1, bed.getUUID().toString());
			addBedLocation.setString(2, bed.getServer());
			addBedLocation.setString(3, info.getWorld());
			addBedLocation.setInt(4, info.getX());
			addBedLocation.setInt(5, info.getY());
			addBedLocation.setInt(6, info.getZ());
			addBedLocation.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				addBedLocation.close();
			} catch (Exception ex) {}
		}
	}

	public List<BedLocation> getAllBedLocations() {
		isConnected();
		List<BedLocation> beds = new ArrayList<BedLocation>();
		PreparedStatement getAllBedLocation = db.prepareStatement(this.getAllBedLocation);
		try {
			ResultSet set = getAllBedLocation.executeQuery();
			while (set.next()) {
				UUID uuid = UUID.fromString(set.getString("uuid"));
				String server = set.getString("server");
				String world_name = set.getString("world_name");
				int x = set.getInt("x");
				int y = set.getInt("y");
				int z = set.getInt("z");
				TeleportInfo info = new TeleportInfo(world_name, server, x, y, z);
				BedLocation bed = new BedLocation(uuid, info);
				beds.add(bed);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				getAllBedLocation.close();
			} catch (Exception ex) {}
		}
		return beds;
	}

	public void removeBed(UUID uuid) {
		isConnected();
		PreparedStatement removeBedLocation = db.prepareStatement(this.removeBedLocation);
		try {
			removeBedLocation.setString(1, uuid.toString());
			removeBedLocation.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				removeBedLocation.close();
			} catch (Exception ex) {}
		}
	}
}
