
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class DataRepository {

	private static final String SERVER_DATA_DIR = "src/sperta/data/server/";
	private static final String USER_FILE = SERVER_DATA_DIR + "user.txt";
	private static final String HOUSES_FILE = SERVER_DATA_DIR + "all_houses.txt";
	private static final String HOUSES_DIR = SERVER_DATA_DIR + "houses/";
	private static final String STATES_DIR = SERVER_DATA_DIR + "states/";
	private static final String LOGS_DIR = SERVER_DATA_DIR + "logs/";
	private static final String CERTS_DIR = SERVER_DATA_DIR + "certs/";
	private static final String SALT_FILE = SERVER_DATA_DIR + "server.salt";
	private static final String[] DEFAULT_SECTIONS = { "E", "G", "L", "M", "P", "S" };

	private static final String PBE_ALGORITHM = "PBEWithHmacSHA256AndAES_128";

	private final Object fileLock = new Object();
	private SecretKey pbeKey;

	public DataRepository(String cipherPassword) {
		initializeStorage();
		this.pbeKey = initPBEKey(cipherPassword);
		verifyAllIntegrity();
	}

	private SecretKey initPBEKey(String password) {
		try {
			byte[] salt = loadOrCreateSalt();
			PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, 20);
			SecretKeyFactory kf = SecretKeyFactory.getInstance(PBE_ALGORITHM);
			return kf.generateSecret(keySpec);
		} catch (Exception e) {
			throw new RuntimeException("Erro ao inicializar chave PBE: " + e.getMessage(), e);
		}
	}

	private byte[] loadOrCreateSalt() {
		try {
			File saltFile = new File(SALT_FILE);
			if (saltFile.exists()) {
				return Files.readAllBytes(saltFile.toPath());
			}
			byte[] salt = new byte[8];
			new SecureRandom().nextBytes(salt);
			Files.write(saltFile.toPath(), salt);
			return salt;
		} catch (IOException e) {
			throw new RuntimeException("Erro ao gerir salt do servidor: " + e.getMessage(), e);
		}
	}

	private byte[] encryptBytes(byte[] plaintext) {
		try {
			Cipher c = Cipher.getInstance(PBE_ALGORITHM);
			c.init(Cipher.ENCRYPT_MODE, pbeKey);
			byte[] ciphertext = c.doFinal(plaintext);
			byte[] params = c.getParameters().getEncoded();

			ByteBuffer buffer = ByteBuffer.allocate(4 + params.length + ciphertext.length);
			buffer.putInt(params.length);
			buffer.put(params);
			buffer.put(ciphertext);
			return buffer.array();
		} catch (Exception e) {
			throw new RuntimeException("Erro ao cifrar dados: " + e.getMessage(), e);
		}
	}

	private byte[] decryptBytes(byte[] encrypted) {
		try {
			ByteBuffer buffer = ByteBuffer.wrap(encrypted);
			int paramsLen = buffer.getInt();
			byte[] paramsBytes = new byte[paramsLen];
			buffer.get(paramsBytes);
			byte[] ciphertext = new byte[buffer.remaining()];
			buffer.get(ciphertext);

			AlgorithmParameters p = AlgorithmParameters.getInstance(PBE_ALGORITHM);
			p.init(paramsBytes);
			Cipher c = Cipher.getInstance(PBE_ALGORITHM);
			c.init(Cipher.DECRYPT_MODE, pbeKey, p);
			return c.doFinal(ciphertext);
		} catch (Exception e) {
			throw new RuntimeException("Erro ao decifrar dados: " + e.getMessage(), e);
		}
	}

	private void writeHash(File file, byte[] plainContent) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(plainContent);
			File hashFile = new File(file.getPath() + ".hash");
			Files.write(hashFile.toPath(), hash);
		} catch (Exception e) {
			throw new RuntimeException("Erro ao escrever hash de integridade: " + e.getMessage(), e);
		}
	}

	private boolean verifyHash(File file, byte[] plainContent) {
		try {
			File hashFile = new File(file.getPath() + ".hash");
			if (!hashFile.exists()) {
				return true; // Ficheiro novo, sem hash anterior
			}
			byte[] storedHash = Files.readAllBytes(hashFile.toPath());
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] computedHash = md.digest(plainContent);
			return MessageDigest.isEqual(storedHash, computedHash);
		} catch (Exception e) {
			return false;
		}
	}

	/**Cifra o conteúdo em claro com PBE, escreve no ficheiro,
	 * e guarda a síntese SHA-256 do conteúdo em claro no ficheiro .hash.*/
	private void secureWriteFile(File file, byte[] plaintext) {
		try {
			File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			byte[] encrypted = encryptBytes(plaintext);
			Files.write(file.toPath(), encrypted);
			writeHash(file, plaintext);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Erro ao escrever ficheiro seguro: " + e.getMessage(), e);
		}
	}

	/**Lê o ficheiro cifrado, decifra com PBE, e verifica a integridade SHA-256.
	 * Se a integridade falhar, imprime NOK-INTEGRITY e termina o servidor.*/
	private byte[] secureReadFile(File file) {
		if (!file.exists()) {
			return null;
		}
		try {
			byte[] encrypted = Files.readAllBytes(file.toPath());
			if (encrypted.length == 0) {
				return null;
			}
			byte[] plaintext = decryptBytes(encrypted);
			if (!verifyHash(file, plaintext)) {
				System.out.println("NOK-INTEGRITY");
				System.exit(-1);
			}
			return plaintext;
		} catch (RuntimeException e) {
			System.out.println("NOK-INTEGRITY");
			System.exit(-1);
			return null;
		} catch (IOException e) {
			System.out.println("NOK-INTEGRITY");
			System.exit(-1);
			return null;
		}
	}


	/**Lê e decifra um ficheiro, devolvendo as linhas de texto.
	 * Se o ficheiro não existir, devolve uma lista vazia.*/
	private List<String> readAllLines(File file) {
		List<String> lines = new ArrayList<>();
		byte[] content = secureReadFile(file);
		if (content == null || content.length == 0) {
			return lines;
		}
		String text = new String(content, StandardCharsets.UTF_8);
		String[] splitLines = text.split("\n", -1);
		for (String line : splitLines) {
			if (line.endsWith("\r")) {
				line = line.substring(0, line.length() - 1);
			}
			lines.add(line);
		}
		while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
			lines.remove(lines.size() - 1);
		}
		return lines;
	}

	/**Serializa as linhas, cifra o conteúdo, e escreve no ficheiro
	 * juntamente com o hash de integridade.*/
	private void writeAllLines(File file, List<String> lines) {
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			sb.append(line).append("\n");
		}
		secureWriteFile(file, sb.toString().getBytes(StandardCharsets.UTF_8));
	}


	private void verifyAllIntegrity() {
		verifyFileIntegrity(new File(USER_FILE));
		verifyFileIntegrity(new File(HOUSES_FILE));
		File housesDir = new File(HOUSES_DIR);
		if (housesDir.exists()) {
			File[] houseFiles = housesDir.listFiles((d, name) -> name.endsWith(".txt"));
			if (houseFiles != null) {
				for (File f : houseFiles) {
					verifyFileIntegrity(f);
				}
			}
		}
		File statesDir = new File(STATES_DIR);
		if (statesDir.exists()) {
			File[] stateFiles = statesDir.listFiles((d, name) -> name.endsWith(".txt"));
			if (stateFiles != null) {
				for (File f : stateFiles) {
					verifyFileIntegrity(f);
				}
			}
		}
		File logsDir = new File(LOGS_DIR);
		if (logsDir.exists()) {
			File[] logHouseDirs = logsDir.listFiles(File::isDirectory);
			if (logHouseDirs != null) {
				for (File houseDir : logHouseDirs) {
					File[] logFiles = houseDir.listFiles((d, name) -> name.endsWith(".csv"));
					if (logFiles != null) {
						for (File f : logFiles) {
							verifyFileIntegrity(f);
						}
					}
				}
			}
		}
		File certsDir = new File(CERTS_DIR);
		if (certsDir.exists()) {
			File[] certFiles = certsDir.listFiles((d, name) -> name.endsWith(".cer"));
			if (certFiles != null) {
				for (File f : certFiles) {
					verifyFileIntegrity(f);
				}
			}
		}
	}

	/**Verifica a integridade de um ficheiro cifrado.
	 * Decifra → calcula hash → compara com .hash guardado.
	 * Se falhar, imprime NOK-INTEGRITY e termina o servidor.*/
	private void verifyFileIntegrity(File file) {
		if (!file.exists() || file.length() == 0) {
			return;
		}
		File hashFile = new File(file.getPath() + ".hash");
		if (!hashFile.exists()) {
			return; // Sem hash = ficheiro novo / primeira execução
		}
		try {
			byte[] encrypted = Files.readAllBytes(file.toPath());
			byte[] plaintext = decryptBytes(encrypted);
			if (!verifyHash(file, plaintext)) {
				System.out.println("NOK-INTEGRITY");
				System.exit(-1);
			}
		} catch (Exception e) {
			System.out.println("NOK-INTEGRITY");
			System.exit(-1);
		}
	}


	private void initializeStorage() {
		new File(SERVER_DATA_DIR).mkdirs();
		new File(HOUSES_DIR).mkdirs();
		new File(STATES_DIR).mkdirs();
		new File(LOGS_DIR).mkdirs();
		new File(CERTS_DIR).mkdirs();
	}


	public String[] getUserRecord(String user) {
		synchronized (fileLock) {
			List<String> lines = readAllLines(new File(USER_FILE));
			for (String line : lines) {
				if (line.trim().isEmpty()) {
					continue;
				}
				String[] parts = line.split(":", 3);
				if (parts.length == 3 && parts[0].equals(user)) {
					return parts; // {user, hashB64, saltB64}
				}
			}
			return null;
		}
	}

	public boolean addUser(String user, String passwd) {
		synchronized (fileLock) {
			try {
				List<String> lines = readAllLines(new File(USER_FILE));
				byte[] salt = CryptoUtils.generateSalt();
				byte[] hash = CryptoUtils.hashPassword(passwd, salt);
				String saltB64 = CryptoUtils.toBase64(salt);
				String hashB64 = CryptoUtils.toBase64(hash);
				lines.add(user + ":" + hashB64 + ":" + saltB64);
				writeAllLines(new File(USER_FILE), lines);
				return true;
			} catch (Exception e) {
				System.err.println("Erro ao registar novo user: " + e.getMessage());
				return false;
			}
		}
	}

	public boolean userExists(String user) {
		return getUserRecord(user) != null;
	}

	public void saveUserCertificate(String user, byte[] certBytes) throws IOException {
		synchronized (fileLock) {
			File certsDir = new File(CERTS_DIR);
			if (!certsDir.exists()) {
				certsDir.mkdirs();
			}
			File certFile = new File(CERTS_DIR + user + ".cer");
			secureWriteFile(certFile, certBytes);
		}
	}

	public byte[] getUserCertificate(String user) throws IOException {
		synchronized (fileLock) {
			File certFile = new File(CERTS_DIR + user + ".cer");
			return secureReadFile(certFile);
		}
	}

	public boolean userHasCertificate(String user) {
		return new File(CERTS_DIR + user + ".cer").exists();
	}

	public boolean houseExists(String hm) {
		synchronized (fileLock) {
			List<String> lines = readAllLines(new File(HOUSES_FILE));
			for (String line : lines) {
				if (line.trim().isEmpty()) {
					continue;
				}
				String[] parts = line.split("\\|", 3);
				if (parts.length >= 1 && parts[0].equals(hm)) {
					return true;
				}
			}
			return false;
		}
	}

	public boolean isOwner(String hm, String user) {
		synchronized (fileLock) {
			List<String> lines = readAllLines(new File(HOUSES_FILE));
			for (String line : lines) {
				if (line.trim().isEmpty()) {
					continue;
				}
				String[] parts = line.split("\\|", 3);
				if (parts.length >= 2 && parts[0].equals(hm) && parts[1].equals(user)) {
					return true;
				}
			}
			return false;
		}
	}

	public void createHouse(String hm, String owner) throws IOException {
		synchronized (fileLock) {
			List<String> housesLines = readAllLines(new File(HOUSES_FILE));
			housesLines.add(hm + "|" + owner + "|" + defaultCountersSerialized());
			writeAllLines(new File(HOUSES_FILE), housesLines);

			File houseFile = new File(HOUSES_DIR + hm + ".txt");
			List<String> houseLines = new ArrayList<>();
			houseLines.add("[permissions]");
			houseLines.add("");
			houseLines.add("[devices]");
			writeAllLines(houseFile, houseLines);
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
			File houseFile = new File(HOUSES_DIR + hm + ".txt");
			List<String> lines = readAllLines(houseFile);
			boolean inDevices = false;
			for (String line : lines) {
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
			return false;
		}
	}

	public String getDeviceSection(String hm, String device) {
		synchronized (fileLock) {
			File houseFile = new File(HOUSES_DIR + hm + ".txt");
			List<String> lines = readAllLines(houseFile);
			boolean inDevices = false;
			for (String line : lines) {
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
			return null;
		}
	}

	public boolean hasPermission(String hm, String user, String section) {
		if (isOwner(hm, user)) {
			return true;
		}

		synchronized (fileLock) {
			File houseFile = new File(HOUSES_DIR + hm + ".txt");
			List<String> lines = readAllLines(houseFile);
			boolean inPermissions = false;
			for (String line : lines) {
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
			return false;
		}
	}

	public boolean hasMembership(String hm, String user) {
		if (isOwner(hm, user)) {
			return true;
		}
		synchronized (fileLock) {
			File houseFile = new File(HOUSES_DIR + hm + ".txt");
			List<String> lines = readAllLines(houseFile);
			boolean inPermissions = false;
			for (String line : lines) {
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
			List<String> stateLines = readAllLines(statesFile);

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
			List<String> logLines = readAllLines(logFile);
			logLines.add(device + "," + value + "," + timestamp);
			writeAllLines(logFile, logLines);
		}
	}

	public byte[] readStateFile(String hm) throws IOException {
		synchronized (fileLock) {
			File statesFile = new File(STATES_DIR + hm + ".txt");
			return secureReadFile(statesFile);
		}
	}

	public byte[] readDeviceLog(String hm, String device) throws IOException {
		synchronized (fileLock) {
			File logFile = new File(LOGS_DIR + hm + "/" + device + ".csv");
			return secureReadFile(logFile);
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
