/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class ClientCommandLoop {

	private static final String DOWNLOAD_DIR = "src/sperta/data/client/downloads";
	private static final String[] ALL_SECTIONS = { "E", "G", "L", "M", "P", "S" };

	private final ObjectOutputStream out;
	private final ObjectInputStream in;
	private final Scanner scanner;
	private final PrivateKey privateKey;
	private final PublicKey publicKey;

	public ClientCommandLoop(ObjectOutputStream out, ObjectInputStream in, Scanner scanner,
			String keystorePath, String keystorePassword) {
		this.out = out;
		this.in = in;
		this.scanner = scanner;
		// Carregar chaves RSA da keystore do utilizador
		PrivateKey privKey = null;
		PublicKey pubKey = null;
		try {
			KeyStore ks = KeyStore.getInstance("PKCS12");
			try (FileInputStream fis = new FileInputStream(keystorePath)) {
				ks.load(fis, keystorePassword.toCharArray());
			}
			java.util.Enumeration<String> aliases = ks.aliases();
			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				if (ks.isKeyEntry(alias)) {
					privKey = (PrivateKey) ks.getKey(alias, keystorePassword.toCharArray());
					Certificate cert = ks.getCertificate(alias);
					if (cert != null) pubKey = cert.getPublicKey();
					break;
				}
			}
		} catch (Exception e) {
			System.err.println("Erro ao carregar keystore: " + e.getMessage());
		}
		this.privateKey = privKey;
		this.publicKey = pubKey;
	}

	public void run() {
		printMenu();
		while (true) {
			System.out.print("> ");
			if (!scanner.hasNextLine()) {
				break;
			}

			String line = scanner.nextLine().trim();
			if (line.isEmpty()) {
				continue;
			}

			String[] parts = line.split("\\s+");
			String cmd = parts[0].toUpperCase();

			try {
				switch (cmd) {
					case "CREATE":
						if (checkArgs(parts, 2, "CREATE <hm>")) handleCreate(parts[1]);
						break;
					case "ADD":
						if (checkArgs(parts, 4, "ADD <user1> <hm> <s>")) handleAdd(parts[1], parts[2], parts[3]);
						break;
					case "RD":
						if (checkArgs(parts, 3, "RD <hm> <s>")) handleRD(parts[1], parts[2]);
						break;
					case "EC":
						if (checkArgs(parts, 4, "EC <hm> <d> <int>")) handleEC(parts[1], parts[2], parts[3]);
						break;
					case "RT":
						if (checkArgs(parts, 2, "RT <hm>")) handleRT(parts[1]);
						break;
					case "RH":
						if (checkArgs(parts, 3, "RH <hm> <d>")) handleRH(parts[1], parts[2]);
						break;
					default:
						System.out.println("Comando desconhecido: " + cmd);
						break;
				}
			} catch (IOException | ClassNotFoundException e) {
				System.err.println("Erro de comunicação: " + e.getMessage());
				break;
			}
		}
	}

	private void printMenu() {
		System.out.println("");
		System.out.println("Comandos disponíveis:");
		System.out.println("  CREATE <hm>           # Criar casa <hm>");
		System.out.println("  ADD <user1> <hm> <s>  # Adicionar <user1> à casa <hm>, seção <s>");
		System.out.println("  RD <hm> <s>           # Registar dispositivo na casa <hm>, seção <s>");
		System.out.println("  EC <hm> <d> <int>     # Enviar valor <int> ao dispositivo <d> da casa <hm>");
		System.out.println("  RT <hm>               # Receber estados de todos os dispositivos da casa <hm>");
		System.out.println("  RH <hm> <d>           # Receber histórico (csv) do dispositivo <d> da casa <hm>");
		System.out.println("");
	}

	private boolean checkArgs(String[] parts, int min, String usage) {
		if (parts.length >= min) {
			return true;
		}
		System.out.println("Uso: " + usage);
		return false;
	}

	private void handleCreate(String hm) throws IOException, ClassNotFoundException {
		out.writeObject("CREATE " + hm);
		out.flush();
		String response = (String) in.readObject();
		System.out.println(response);

		if ("OK".equals(response)) {
			// E2E: Gerar chave AES-128 por secção e enviar wrapped com a nossa chave pública RSA
			try {
				out.writeInt(ALL_SECTIONS.length);
				for (String section : ALL_SECTIONS) {
					SecretKey sectionKey = generateAESKey();
					byte[] wrappedKey = wrapKey(sectionKey, publicKey);
					out.writeObject(section);
					out.writeInt(wrappedKey.length);
					out.write(wrappedKey);
				}
				out.flush();
				String keysResponse = (String) in.readObject();
				System.out.println("Chaves de secção: " + keysResponse);
			} catch (Exception e) {
				System.err.println("Erro ao gerar/enviar chaves de secção: " + e.getMessage());
			}
		}
	}

	private void handleAdd(String user1, String hm, String s) throws IOException, ClassNotFoundException {
		out.writeObject("ADD " + user1 + " " + hm + " " + s);
		out.flush();
		String response = (String) in.readObject();

		if ("OK-KEYS".equals(response)) {
			try {
				// Receber certificado do user1
				int certLen = in.readInt();
				byte[] certBytes = new byte[certLen];
				in.readFully(certBytes);
				PublicKey user1PublicKey = extractPublicKeyFromCert(certBytes);

				// Receber chaves wrapped do owner (nós)
				int numSections = in.readInt();
				out.writeInt(numSections);
				for (int i = 0; i < numSections; i++) {
					String section = (String) in.readObject();
					int keyLen = in.readInt();
					byte[] ownerWrappedKey = new byte[keyLen];
					in.readFully(ownerWrappedKey);

					// Unwrap com a nossa PrivateKey, re-wrap com PublicKey do user1
					SecretKey sectionKey = unwrapKey(ownerWrappedKey, privateKey);
					byte[] user1WrappedKey = wrapKey(sectionKey, user1PublicKey);

					out.writeObject(section);
					out.writeInt(user1WrappedKey.length);
					out.write(user1WrappedKey);
				}
				out.flush();

				// Receber confirmação final
				String finalResponse = (String) in.readObject();
				System.out.println(finalResponse);
			} catch (Exception e) {
				System.err.println("Erro na partilha de chaves ADD: " + e.getMessage());
			}
		} else {
			System.out.println(response);
		}
	}

	private void handleRD(String hm, String s) throws IOException, ClassNotFoundException {
		out.writeObject("RD " + hm + " " + s);
		out.flush();
		String response = (String) in.readObject();
		System.out.println(response);
	}

	private void handleEC(String hm, String d, String intVal) throws IOException, ClassNotFoundException {
		out.writeObject("EC " + hm + " " + d + " " + intVal);
		out.flush();
		String response = (String) in.readObject();

		if ("OK-KEY".equals(response)) {
			try {
				// Receber wrapped key da secção
				int keyLen = in.readInt();
				byte[] wrappedKey = new byte[keyLen];
				in.readFully(wrappedKey);

				// Unwrap e cifrar o valor com AES
				SecretKey sectionKey = unwrapKey(wrappedKey, privateKey);
				byte[] valBytes = intVal.getBytes();
				byte[] encryptedVal = encryptAES(valBytes, sectionKey);

				// Enviar valor cifrado
				out.writeInt(encryptedVal.length);
				out.write(encryptedVal);
				out.flush();

				// Receber confirmação
				String finalResponse = (String) in.readObject();
				System.out.println(finalResponse);
			} catch (Exception e) {
				System.err.println("Erro ao cifrar valor EC: " + e.getMessage());
			}
		} else {
			System.out.println(response);
		}
	}

	private void handleRT(String hm) throws IOException, ClassNotFoundException {
		out.writeObject("RT " + hm);
		out.flush();
		String response = (String) in.readObject();
		if ("OK".equals(response)) {
			try {
				// E2E: Receber chaves wrapped por secção
				int numKeys = in.readInt();
				Map<String, SecretKey> sectionKeys = new HashMap<>();
				for (int i = 0; i < numKeys; i++) {
					String sec = (String) in.readObject();
					int keyLen = in.readInt();
					if (keyLen > 0) {
						byte[] wrappedKey = new byte[keyLen];
						in.readFully(wrappedKey);
						sectionKeys.put(sec, unwrapKey(wrappedKey, privateKey));
					}
				}

				// Receber dados
				long size = in.readLong();
				byte[] data = new byte[(int) size];
				in.readFully(data);

				// Decifrar cada linha: device|encB64|timestamp
				String text = new String(data, "UTF-8");
				String[] lines = text.split("\n");
				StringBuilder decrypted = new StringBuilder();
				for (String line : lines) {
					if (line.trim().isEmpty()) continue;
					String[] parts = line.split("\\|", 3);
					if (parts.length >= 2) {
						String deviceName = parts[0];
						// Inferir secção do nome do device (ex: "E1" → "E")
						String sec = deviceName.replaceAll("[0-9]", "");
						SecretKey key = sectionKeys.get(sec);
						if (key != null) {
							try {
								byte[] encBytes = Base64.getDecoder().decode(parts[1]);
								byte[] plainBytes = decryptAES(encBytes, key);
								String plainVal = new String(plainBytes);
								decrypted.append(deviceName).append("|").append(plainVal);
								if (parts.length == 3) decrypted.append("|").append(parts[2]);
								decrypted.append("\n");
							} catch (Exception e) {
								decrypted.append(line).append("\n");
							}
						} else {
							decrypted.append(line).append("\n");
						}
					} else {
						decrypted.append(line).append("\n");
					}
				}

				String fileName = hm + "_states.txt";
				String outputPath = resolveOutputPath(fileName);
				try (FileOutputStream fos = new FileOutputStream(outputPath)) {
					fos.write(decrypted.toString().getBytes("UTF-8"));
				}
				System.out.println("OK. Estados decifrados guardados em " + outputPath);
			} catch (Exception e) {
				System.err.println("Erro ao decifrar estados RT: " + e.getMessage());
			}
		} else {
			System.out.println(response);
		}
	}

	private void handleRH(String hm, String d) throws IOException, ClassNotFoundException {
		out.writeObject("RH " + hm + " " + d);
		out.flush();
		String response = (String) in.readObject();
		if ("OK".equals(response)) {
			try {
				// E2E: Receber chave wrapped da secção
				int keyLen = in.readInt();
				SecretKey sectionKey = null;
				if (keyLen > 0) {
					byte[] wrappedKey = new byte[keyLen];
					in.readFully(wrappedKey);
					sectionKey = unwrapKey(wrappedKey, privateKey);
				}

				// Receber dados
				long size = in.readLong();
				byte[] data = new byte[(int) size];
				in.readFully(data);

				// Decifrar cada linha CSV: device,encB64,timestamp
				String text = new String(data, "UTF-8");
				String[] lines = text.split("\n");
				StringBuilder decrypted = new StringBuilder();
				for (String line : lines) {
					if (line.trim().isEmpty()) continue;
					String[] parts = line.split(",", 3);
					if (parts.length >= 2 && sectionKey != null) {
						try {
							byte[] encBytes = Base64.getDecoder().decode(parts[1]);
							byte[] plainBytes = decryptAES(encBytes, sectionKey);
							String plainVal = new String(plainBytes);
							decrypted.append(parts[0]).append(",").append(plainVal);
							if (parts.length == 3) decrypted.append(",").append(parts[2]);
							decrypted.append("\n");
						} catch (Exception e) {
							decrypted.append(line).append("\n");
						}
					} else {
						decrypted.append(line).append("\n");
					}
				}

				String fileName = hm + "_" + d + ".csv";
				String outputPath = resolveOutputPath(fileName);
				try (FileOutputStream fos = new FileOutputStream(outputPath)) {
					fos.write(decrypted.toString().getBytes("UTF-8"));
				}
				System.out.println("OK. Historico decifrado guardado em " + outputPath);
			} catch (Exception e) {
				System.err.println("Erro ao decifrar historico RH: " + e.getMessage());
			}
		} else {
			System.out.println(response);
		}
	}

	private String resolveOutputPath(String fileName) {
		File dir = new File(DOWNLOAD_DIR);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return new File(dir, fileName).getPath();
	}

	// ======================== Crypto Helpers (E2E) ========================

	/**Gera uma chave AES de 128 bits.*/
	protected SecretKey generateAESKey() throws Exception {
		KeyGenerator kg = KeyGenerator.getInstance("AES");
		kg.init(128);
		return kg.generateKey();
	}

	/**Cifra (wrap) uma chave simétrica com uma chave pública RSA.*/
	protected byte[] wrapKey(SecretKey secretKey, PublicKey pubKey) throws Exception {
		Cipher c = Cipher.getInstance("RSA");
		c.init(Cipher.WRAP_MODE, pubKey);
		return c.wrap(secretKey);
	}

	/**Decifra (unwrap) uma chave simétrica AES com a chave privada RSA.*/
	protected SecretKey unwrapKey(byte[] wrappedKey, PrivateKey privKey) throws Exception {
		Cipher c = Cipher.getInstance("RSA");
		c.init(Cipher.UNWRAP_MODE, privKey);
		return (SecretKey) c.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
	}

	/**Cifra dados com AES.*/
	protected byte[] encryptAES(byte[] data, SecretKey key) throws Exception {
		Cipher c = Cipher.getInstance("AES");
		c.init(Cipher.ENCRYPT_MODE, key);
		return c.doFinal(data);
	}

	/**Decifra dados com AES.*/
	protected byte[] decryptAES(byte[] data, SecretKey key) throws Exception {
		Cipher c = Cipher.getInstance("AES");
		c.init(Cipher.DECRYPT_MODE, key);
		return c.doFinal(data);
	}

	/**Extrai PublicKey de um certificado X.509 em bytes.*/
	protected PublicKey extractPublicKeyFromCert(byte[] certBytes) throws Exception {
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));
		return cert.getPublicKey();
	}
}
