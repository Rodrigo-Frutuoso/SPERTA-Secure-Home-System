/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataRepository {

	private static final String SERVER_DATA_DIR = "src/sperta/data/server/";
	private static final String USER_FILE = SERVER_DATA_DIR + "user.txt";
	private static final String HOUSES_FILE = SERVER_DATA_DIR + "all_houses.txt";
	private static final String HOUSES_DIR = SERVER_DATA_DIR + "houses/";
	private static final String STATES_DIR = SERVER_DATA_DIR + "states/";
	private static final String LOGS_DIR = SERVER_DATA_DIR + "logs/";
	private static final String[] DEFAULT_SECTIONS = {"E", "G", "L", "M", "P", "S"};

	private final Object fileLock = new Object();

	public DataRepository() {
		initializeStorage();
	}

	private void initializeStorage() {
		try {
			File userFile = new File(USER_FILE);
			File housesFile = new File(HOUSES_FILE);
			File housesDir = new File(HOUSES_DIR);
			File statesDir = new File(STATES_DIR);
			File logsDir = new File(LOGS_DIR);

			File userParent = userFile.getParentFile();
			if (userParent != null && !userParent.exists()) {
				userParent.mkdirs();
			}

			File housesParent = housesFile.getParentFile();
			if (housesParent != null && !housesParent.exists()) {
				housesParent.mkdirs();
			}

			if (!housesDir.exists()) {
				housesDir.mkdirs();
			}
			if (!statesDir.exists()) {
				statesDir.mkdirs();
			}
			if (!logsDir.exists()) {
				logsDir.mkdirs();
			}

			if (!userFile.exists()) {
				userFile.createNewFile();
			}
			if (!housesFile.exists()) {
				housesFile.createNewFile();
			}
		} catch (IOException e) {
			throw new IllegalStateException("Nao foi possivel inicializar a pasta de dados: " + e.getMessage(), e);
		}
	}

	public String getUserPassword(String user) {
		synchronized (fileLock) {
			try (BufferedReader reader = new BufferedReader(new FileReader(USER_FILE))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty()) {
						continue;
					}
					String[] parts = line.split(":", 2);
					if (parts.length == 2 && parts[0].equals(user)) {
						return parts[1];
					}
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler ficheiro de users: " + e.getMessage());
			}
			return null;
		}
	}

	public boolean addUser(String user, String passwd) {
		synchronized (fileLock) {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FILE, true))) {
				writer.write(user + ":" + passwd);
				writer.newLine();
				return true;
			} catch (IOException e) {
				System.err.println("Erro ao registar novo user: " + e.getMessage());
				return false;
			}
		}
	}

	public boolean userExists(String user) {
		return getUserPassword(user) != null;
	}

	public boolean houseExists(String hm) {
		if (!new File(HOUSES_FILE).exists()) {
			return false;
		}
		synchronized (fileLock) {
			try (BufferedReader reader = new BufferedReader(new FileReader(HOUSES_FILE))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty()) {
						continue;
					}
					String[] parts = line.split("\\|", 3);
					if (parts.length >= 1 && parts[0].equals(hm)) {
						return true;
					}
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler houses: " + e.getMessage());
			}
			return false;
		}
	}

	public boolean isOwner(String hm, String user) {
		if (!new File(HOUSES_FILE).exists()) {
			return false;
		}
		synchronized (fileLock) {
			try (BufferedReader reader = new BufferedReader(new FileReader(HOUSES_FILE))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty()) {
						continue;
					}
					String[] parts = line.split("\\|", 3);
					if (parts.length >= 2 && parts[0].equals(hm) && parts[1].equals(user)) {
						return true;
					}
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler houses: " + e.getMessage());
			}
			return false;
		}
	}

	public void createHouse(String hm, String owner) throws IOException {
		synchronized (fileLock) {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(HOUSES_FILE, true))) {
				writer.write(hm + "|" + owner + "|" + defaultCountersSerialized());
				writer.newLine();
			}

			File houseFile = new File(HOUSES_DIR + hm + ".txt");
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(houseFile))) {
				writer.write("[permissions]");
				writer.newLine();
				writer.newLine();
				writer.write("[devices]");
				writer.newLine();
			}
		}
	}

	public void addPermission(String hm, String user1, String section) throws IOException {
		synchronized (fileLock) {
			File houseFile = new File(HOUSES_DIR + hm + ".txt");
			List<String> lines = readAllLines(houseFile);

			boolean inPermissions = false;
			boolean userFound = false;
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line.trim().equals("[permissions]")) {
					inPermissions = true;
					continue;
				}
				if (line.trim().equals("[devices]")) {
					break;
				}
				if (!inPermissions || line.trim().isEmpty()) {
					continue;
				}
				String[] parts = line.split("\\|", 2);
				if (parts.length == 2 && parts[0].equals(user1)) {
					if (!parts[1].equals("all") && !section.equals("all")) {
						Set<String> sections = new LinkedHashSet<>(Arrays.asList(parts[1].split(",")));
						sections.add(section);
						lines.set(i, user1 + "|" + String.join(",", sections));
					} else {
						lines.set(i, user1 + "|all");
					}
					userFound = true;
					break;
				}
			}

			if (!userFound) {
				int devicesIdx = lines.indexOf("[devices]");
				if (devicesIdx == -1) {
					devicesIdx = lines.size();
				}
				lines.add(devicesIdx, user1 + "|" + section);
			}

			writeAllLines(houseFile, lines);
		}
	}

	public void registerDevice(String hm, String section) throws IOException {
		synchronized (fileLock) {
			String normalizedSection = section == null ? "" : section.trim().toUpperCase();
			if (!isValidSection(normalizedSection)) {
				throw new IOException("Secao invalida: " + section);
			}

			File housesFile = new File(HOUSES_FILE);
			List<String> houseLines = readAllLines(housesFile);
			int houseLineIndex = -1;
			String owner = null;
			Map<String, Integer> counters = null;

			for (int i = 0; i < houseLines.size(); i++) {
				String line = houseLines.get(i);
				if (line.trim().isEmpty()) {
					continue;
				}

				String[] parts = line.split("\\|", 3);
				if (parts.length >= 2 && parts[0].equals(hm)) {
					houseLineIndex = i;
					owner = parts[1];
					String serialized = parts.length == 3 ? parts[2] : defaultCountersSerialized();
					counters = parseCounters(serialized);
					break;
				}
			}

			if (houseLineIndex == -1 || owner == null || counters == null) {
				throw new IOException("Casa nao encontrada: " + hm);
			}

			int counter = counters.getOrDefault(normalizedSection, 1);
			String deviceName = normalizedSection + counter;
			counters.put(normalizedSection, counter + 1);

			houseLines.set(houseLineIndex, hm + "|" + owner + "|" + countersToSerialized(counters));
			writeAllLines(housesFile, houseLines);

			File houseFile = new File(HOUSES_DIR + hm + ".txt");
			List<String> lines = readAllLines(houseFile);
			lines.add(deviceName + "|" + normalizedSection);
			writeAllLines(houseFile, lines);
		}
	}

	public boolean isValidSection(String section) {
		if (section == null) {
			return false;
		}
		String normalized = section.trim().toUpperCase();
		for (String allowed : DEFAULT_SECTIONS) {
			if (allowed.equals(normalized)) {
				return true;
			}
		}
		return false;
	}

	public boolean deviceExists(String hm, String device) {
		synchronized (fileLock) {
			String houseFilePath = HOUSES_DIR + hm + ".txt";
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFilePath))) {
				String line;
				boolean inDevices = false;
				while ((line = reader.readLine()) != null) {
					if (line.trim().equals("[devices]")) {
						inDevices = true;
						continue;
					}
					if (!inDevices || line.trim().isEmpty()) {
						continue;
					}
					String[] parts = line.split("\\|", 2);
					if (parts.length >= 1 && parts[0].equals(device)) {
						return true;
					}
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler dispositivos: " + e.getMessage());
			}
			return false;
		}
	}

	public String getDeviceSection(String hm, String device) {
		synchronized (fileLock) {
			String houseFilePath = HOUSES_DIR + hm + ".txt";
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFilePath))) {
				String line;
				boolean inDevices = false;
				while ((line = reader.readLine()) != null) {
					if (line.trim().equals("[devices]")) {
						inDevices = true;
						continue;
					}
					if (!inDevices || line.trim().isEmpty()) {
						continue;
					}
					String[] parts = line.split("\\|", 2);
					if (parts.length == 2 && parts[0].equals(device)) {
						return parts[1].trim();
					}
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler secção do dispositivo: " + e.getMessage());
			}
			return null;
		}
	}

	public boolean hasPermission(String hm, String user, String section) {
		if (isOwner(hm, user)) {
			return true;
		}

		synchronized (fileLock) {
			String houseFilePath = HOUSES_DIR + hm + ".txt";
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFilePath))) {
				String line;
				boolean inPermissions = false;
				while ((line = reader.readLine()) != null) {
					if (line.trim().equals("[permissions]")) {
						inPermissions = true;
						continue;
					}
					if (line.trim().equals("[devices]")) {
						break;
					}
					if (!inPermissions || line.trim().isEmpty()) {
						continue;
					}

					String[] parts = line.split("\\|", 2);
					if (parts.length == 2 && parts[0].equals(user)) {
						String[] sections = parts[1].split(",");
						for (String sec : sections) {
							if (sec.trim().equals("all") || sec.trim().equals(section)) {
								return true;
							}
						}
					}
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler permissões: " + e.getMessage());
			}
			return false;
		}
	}

	public boolean hasMembership(String hm, String user) {
		if (isOwner(hm, user)) {
			return true;
		}
		synchronized (fileLock) {
			String houseFilePath = HOUSES_DIR + hm + ".txt";
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFilePath))) {
				String line;
				boolean inPermissions = false;
				while ((line = reader.readLine()) != null) {
					if (line.trim().equals("[permissions]")) {
						inPermissions = true;
						continue;
					}
					if (line.trim().equals("[devices]")) {
						break;
					}
					if (!inPermissions || line.trim().isEmpty()) {
						continue;
					}
					String[] parts = line.split("\\|", 2);
					if (parts.length >= 1 && parts[0].equals(user)) {
						return true;
					}
				}
			} catch (IOException e) {
				return false;
			}
			return false;
		}
	}

	public void updateStateAndLog(String hm, String device, int value) throws IOException {
		synchronized (fileLock) {
			String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

			File statesDir = new File(STATES_DIR);
			if (!statesDir.exists()) {
				statesDir.mkdirs();
			}
			File statesFile = new File(STATES_DIR + hm + ".txt");
			List<String> stateLines = statesFile.exists() ? readAllLines(statesFile) : new ArrayList<>();

			boolean found = false;
			for (int i = 0; i < stateLines.size(); i++) {
				String[] parts = stateLines.get(i).split("\\|", 3);
				if (parts.length >= 1 && parts[0].equals(device)) {
					stateLines.set(i, device + "|" + value + "|" + timestamp);
					found = true;
					break;
				}
			}
			if (!found) {
				stateLines.add(device + "|" + value + "|" + timestamp);
			}
			writeAllLines(statesFile, stateLines);

			File logsHouseDir = new File(LOGS_DIR + hm);
			if (!logsHouseDir.exists()) {
				logsHouseDir.mkdirs();
			}
			File logFile = new File(LOGS_DIR + hm + "/" + device + ".csv");
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
				writer.write(device + "," + value + "," + timestamp);
				writer.newLine();
			}
		}
	}

	public byte[] readStateFile(String hm) throws IOException {
		synchronized (fileLock) {
			File statesFile = new File(STATES_DIR + hm + ".txt");
			if (!statesFile.exists() || statesFile.length() == 0) {
				return null;
			}
			return Files.readAllBytes(statesFile.toPath());
		}
	}

	public byte[] readDeviceLog(String hm, String device) throws IOException {
		synchronized (fileLock) {
			File logFile = new File(LOGS_DIR + hm + "/" + device + ".csv");
			if (!logFile.exists() || logFile.length() == 0) {
				return null;
			}
			return Files.readAllBytes(logFile.toPath());
		}
	}

	private List<String> readAllLines(File file) throws IOException {
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		}
		return lines;
	}

	private void writeAllLines(File file, List<String> lines) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
			for (String line : lines) {
				writer.write(line);
				writer.newLine();
			}
		}
	}

	private String defaultCountersSerialized() {
		Map<String, Integer> counters = new LinkedHashMap<>();
		for (String section : DEFAULT_SECTIONS) {
			counters.put(section, 1);
		}
		return countersToSerialized(counters);
	}

	private Map<String, Integer> parseCounters(String serialized) {
		Map<String, Integer> counters = new LinkedHashMap<>();
		for (String section : DEFAULT_SECTIONS) {
			counters.put(section, 1);
		}

		if (serialized == null || serialized.trim().isEmpty()) {
			return counters;
		}

		String[] parts = serialized.split(",");
		for (String part : parts) {
			String[] kv = part.split("=", 2);
			if (kv.length != 2) {
				continue;
			}

			String section = kv[0].trim().toUpperCase();
			if (!isValidSection(section)) {
				continue;
			}

			try {
				int value = Integer.parseInt(kv[1].trim());
				if (value < 1) {
					value = 1;
				}
				counters.put(section, value);
			} catch (NumberFormatException ignored) {
			}
		}

		return counters;
	}

	private String countersToSerialized(Map<String, Integer> counters) {
		List<String> entries = new ArrayList<>();
		for (String section : DEFAULT_SECTIONS) {
			int value = counters.getOrDefault(section, 1);
			entries.add(section + "=" + value);
		}
		return String.join(",", entries);
	}
}
