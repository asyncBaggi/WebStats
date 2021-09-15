package nl.dantevg.webstats;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.json.simple.JSONObject;

import java.util.*;

public class PlaceholderSource {
	private final Map<String, Object> placeholders;
	
	public PlaceholderSource() throws ConfigurationException {
		ConfigurationSection section = WebStats.config.getConfigurationSection("placeholders");
		if (section == null) {
			throw new ConfigurationException("Invalid configuration: placeholders should be a key-value map");
		}
		placeholders = section.getValues(false);
	}
	
	private Set<String> getEntries() {
		Set<String> entries = new HashSet<>();
		for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
			entries.add(player.getName());
		}
		return entries;
	}
	
	private Map<String, JSONObject> getScores() {
		Map<String, JSONObject> values = new HashMap<>();
		// Also get players from EssentialsX's userMap, for offline servers
		Set<OfflinePlayer> players = (!Bukkit.getOnlineMode() && WebStats.hasEssentials)
				? EssentialsHelper.getOfflinePlayers()
				: new HashSet<>();
		players.addAll(Arrays.asList(Bukkit.getOfflinePlayers()));
		
		for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
			String placeholder = entry.getKey();
			String placeholderName = (String) entry.getValue();
			JSONObject scores = new JSONObject();
			for (OfflinePlayer player : players) {
				scores.put(player.getName(), PlaceholderAPI.setPlaceholders(player, placeholder));
			}
			values.put(placeholderName, scores);
		}
		return values;
	}
	
	public EntriesScores getStats() {
		return new EntriesScores(getEntries(), getScores());
	}
	
}
