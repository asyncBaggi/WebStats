package nl.dantevg.webstats.placeholder;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import nl.dantevg.webstats.WebStats;
import nl.dantevg.webstats.database.DatabaseConnection;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlaceholderStorage {
	private static final String TABLE_NAME = "WebStats_placeholders";
	
	private final PlaceholderSource placeholderSource;
	private final HashBasedTable<UUID, String, String> data = HashBasedTable.create();
	private final boolean saveOnPluginDisable;
	
	private final @NotNull DatabaseConnection conn;
	
	public PlaceholderStorage(PlaceholderSource placeholderSource) throws InvalidConfigurationException {
		WebStats.logger.log(Level.INFO, "Enabling placeholder storer");
		
		this.placeholderSource = placeholderSource;
		
		// Register events
		saveOnPluginDisable = WebStats.config.getBoolean("save-placeholders-on-plugin-disable");
		Bukkit.getPluginManager().registerEvents(
				new PlaceholderListener(this, saveOnPluginDisable),
				WebStats.getPlugin(WebStats.class));
		
		// Connect to database
		String hostname = WebStats.config.getString("database.hostname");
		String username = WebStats.config.getString("database.username");
		String password = WebStats.config.getString("database.password");
		String dbname = WebStats.config.getString("store-placeholders-database");
		
		if (hostname == null || username == null || password == null || dbname == null) {
			throw new InvalidConfigurationException("Invalid configuration: missing hostname, username, password or database name");
		}
		
		conn = new DatabaseConnection(hostname, username, password, dbname);
		if (!conn.connect()) return;
		
		// Create table on first use
		if (isFirstUse()) init();
		
		// Read persistently stored data
		load();
		
		// Update stored data with potentially new data
		update();
	}
	
	public boolean disconnect() {
		// Don't save on server close if we already saved on plugin disable
		if (saveOnPluginDisable) return true;
		
		// since this is called when the server closes,
		// save all data to persistent database storage now
		try {
			saveAll();
		} catch (IllegalStateException e) {
			// Catch this exception to add a helpful message before
			// https://github.com/Dantevg/WebStats/issues/30
			if (e.getMessage().equals("zip file closed")) {
				WebStats.logger.log(Level.SEVERE, "A plugin providing PlaceholderAPI placeholders " +
						"was disabled before WebStats could save the latest placeholders. You can set " +
						"'save-placeholders-on-plugin-disable' to true in the config as a workaround.\n" +
						"Github issue: https://github.com/Dantevg/WebStats/issues/30\n" +
						"Here is the stack trace for your interest:", e);
			}else{
				// Rethrow, not the right error message
				throw e;
			}
		}
		return conn.disconnect();
	}
	
	private boolean isFirstUse() {
		try (ResultSet resultSet = conn.getConnection().getMetaData()
				.getTables(null, null, TABLE_NAME, null)) {
			return !resultSet.next();
		} catch (SQLException e) {
			WebStats.logger.log(Level.WARNING, "Could not query placeholder database " + conn.getDBName(), e);
		}
		return false;
	}
	
	private void init() {
		try (PreparedStatement stmt = conn.getConnection().prepareStatement("CREATE TABLE "
				+ TABLE_NAME
				+ " (uuid VARCHAR(36) NOT NULL, "
				+ "placeholder VARCHAR(255) NOT NULL, "
				+ "value VARCHAR(255), "
				+ "PRIMARY KEY(uuid, placeholder));")) {
			stmt.executeUpdate();
			WebStats.logger.log(Level.INFO, "Created new placeholder table "
					+ TABLE_NAME + " in placeholder database " + conn.getDBName());
		} catch (SQLException e) {
			WebStats.logger.log(Level.SEVERE, "Could not initialise placeholder database " + conn.getDBName(), e);
		}
	}
	
	private void load() {
		try (PreparedStatement stmt = conn.getConnection()
				.prepareStatement("SELECT * FROM " + TABLE_NAME + ";");
		     ResultSet resultSet = stmt.executeQuery()) {
			int nRows = 0;
			while (resultSet.next()) {
				UUID uuid = UUID.fromString(resultSet.getString("uuid"));
				String placeholder = resultSet.getString("placeholder");
				String value = resultSet.getString("value");
				data.put(uuid, placeholder, value);
				nRows++;
				WebStats.logger.log(Level.CONFIG, String.format("Loaded %s (%s): %s = %s",
						uuid.toString(), Bukkit.getOfflinePlayer(uuid).getName(), placeholder, value));
			}
			WebStats.logger.log(Level.INFO, "Loaded " + nRows + " rows from database");
		} catch (SQLException e) {
			WebStats.logger.log(Level.SEVERE, "Could not query placeholder database " + conn.getDBName(), e);
		}
	}
	
	private void update() {
		for (OfflinePlayer player : placeholderSource.getEntriesAsPlayers()) {
			placeholderSource.getScoresForPlayer(player).forEach((String placeholder, String value) -> {
				data.put(player.getUniqueId(), placeholder, value);
				WebStats.logger.log(Level.CONFIG, String.format("Updated %s (%s): %s = %s",
						player.getUniqueId().toString(), player.getName(), placeholder, value));
			});
		}
	}
	
	// Store placeholder data for player
	// Returns the amount of scores it saved to the database for this player
	public int save(@NotNull OfflinePlayer player) {
		Map<String, String> scores = placeholderSource.getScoresForPlayer(player);
		UUID uuid = player.getUniqueId();
		
		if (scores.isEmpty()) return 0;
		
		// Store in instance
		scores.forEach((placeholder, value) -> data.put(uuid, placeholder, value));
		
		// Store in database
		String uuidStr = uuid.toString();
		try (PreparedStatement stmt = conn.getConnection()
				.prepareStatement("REPLACE INTO " + TABLE_NAME + " VALUES (?, ?, ?);")) {
			for (Map.Entry<String, String> entry : scores.entrySet()) {
				stmt.setString(1, uuidStr);
				stmt.setString(2, entry.getKey());
				stmt.setString(3, entry.getValue());
				stmt.addBatch();
				WebStats.logger.log(Level.CONFIG, String.format("Saving %s (%s): %s = %s",
						uuidStr, Bukkit.getOfflinePlayer(uuid).getName(), entry.getKey(), entry.getValue()));
			}
			stmt.executeBatch();
			WebStats.logger.log(Level.INFO, "Saved " + scores.size()
					+ " placeholders for player " + player.getName());
			return scores.size();
		} catch (SQLException e) {
			WebStats.logger.log(Level.SEVERE, "Could not update placeholder database " + conn.getDBName(), e);
			return 0;
		}
	}
	
	// Store placeholder data for all players
	public void saveAll() {
		int nRows = 0;
		for (OfflinePlayer player : placeholderSource.getEntriesAsPlayers()) nRows += save(player);
		WebStats.logger.log(Level.INFO, "Saved all placeholders (" + nRows + " rows) to database");
	}
	
	public @Nullable String getScore(UUID uuid, String placeholder) {
		return data.get(uuid, placeholder);
	}
	
	protected @NotNull String debug() {
		try {
			String status = conn.isConnected() ? "connected" : "closed";
			
			List<String> loadedScores = new ArrayList<>();
			for (Table.Cell<UUID, String, String> cell : data.cellSet()) {
				UUID uuid = cell.getRowKey();
				if (uuid == null) continue;
				String playerName = Bukkit.getOfflinePlayer(uuid).getName();
				loadedScores.add(String.format("%s (%s): %s = %s",
						uuid.toString(), playerName, cell.getColumnKey(), cell.getValue()));
			}
			
			return "Placeholder storage database connection: " + status
					+ "\nLoaded placeholders:\n" + String.join("\n", loadedScores);
		} catch (SQLException e) {
			return ""; // Happens only if timeout is < 0, but timeout is 1 here
		}
	}
	
}
