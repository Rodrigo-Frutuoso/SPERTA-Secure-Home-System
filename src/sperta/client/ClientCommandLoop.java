/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Scanner;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

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
	}

	private void handleAdd(String user1, String hm, String s) throws IOException, ClassNotFoundException {
		out.writeObject("ADD " + user1 + " " + hm + " " + s);
		out.flush();
		String response = (String) in.readObject();
		System.out.println(response);
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
		System.out.println(response);
	}

	private void handleRT(String hm) throws IOException, ClassNotFoundException {
		out.writeObject("RT " + hm);
		out.flush();
		String response = (String) in.readObject();
		if ("OK".equals(response)) {
			long size = in.readLong();
			byte[] data = new byte[(int) size];
			in.readFully(data);
			String fileName = hm + "_states.txt";
			String outputPath = resolveOutputPath(fileName);
			try (FileOutputStream fos = new FileOutputStream(outputPath)) {
				fos.write(data);
			}
			System.out.println("OK, " + size + " (long), seguido de " + size + " bytes de dados. Estados guardados em " + outputPath);
		} else {
			System.out.println(response);
		}
	}

	private void handleRH(String hm, String d) throws IOException, ClassNotFoundException {
		out.writeObject("RH " + hm + " " + d);
		out.flush();
		String response = (String) in.readObject();
		if ("OK".equals(response)) {
			long size = in.readLong();
			byte[] data = new byte[(int) size];
			in.readFully(data);
			String fileName = hm + "_" + d + ".csv";
			String outputPath = resolveOutputPath(fileName);
			try (FileOutputStream fos = new FileOutputStream(outputPath)) {
				fos.write(data);
			}
			System.out.println("OK, " + size + " (long), seguido de " + size + " bytes de dados. Historico guardado em " + outputPath);
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
