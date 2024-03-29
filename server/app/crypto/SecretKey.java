package crypto;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import io.seruco.encoding.base62.Base62;

public class SecretKey {
	private static final byte VERSION = (byte) 0xBA;
	private static final int TAG_LENGTH = 16;
	private static final int HEADER_LENGTH = 29;
	private final byte[] key;

	public SecretKey() {
		byte[] secretKey = new byte[32];
		new Random().nextBytes(secretKey);
		this.key = Arrays.copyOf(secretKey, secretKey.length);
	}
	
	public SecretKey(byte[] secretKey) {
		if (secretKey.length != 32) {
			throw new IllegalArgumentException("Secret key must be 32 bytes");
		}
		this.key = Arrays.copyOf(secretKey, secretKey.length);
	}

	public byte[] seal(byte[] plaintext) {
		return seal(plaintext, Bytes.makeRandomNonce());
	}

	public byte[] seal(byte[] plaintext, byte[] nonce) {

		ByteBuffer header = ByteBuffer.allocate(1 + 4 + 24);

		/*
		 * Version (1B)
		 */
		header.put(0, VERSION);
		header.position(1);

		/*
		 * Timestamp (4B)
		 */
		byte[] timestamp = Time.makeTimestamp();
		header.put(timestamp, 0, timestamp.length);
		header.position(5);

		/*
		 * Nonce (24B)
		 */
		header.put(nonce, 0, nonce.length);
		header.position(29);

		/*
		 * Ciphertext (*B)
		 */
		byte[] cipherAndTag = encrypt(header.array(), plaintext, nonce);

		byte[] headerAndCipher = Bytes.addAll(header.array(), cipherAndTag);

		return Bytes.base62Encode(headerAndCipher);
	}

	private byte[] encrypt(byte[] header, byte[] plaintext, byte[] nonce) {
		CipherParameters cp = new KeyParameter(key);
		ParametersWithIV params = new ParametersWithIV(cp, nonce);
		StreamCipher engine = new XChaCha20Engine();
		engine.init(true, params);
		byte[] encrypted = new byte[plaintext.length + TAG_LENGTH];

		engine.processBytes(plaintext, 0, plaintext.length, encrypted, 0);

		/*
		 * Generate Poly13509 and append to cipher
		 */
		final Poly1305 poly1305 = new Poly1305();
		poly1305.init(cp);
		poly1305.update(header, 0, header.length);
		poly1305.doFinal(encrypted, plaintext.length);
		return encrypted;
	}

	private byte[] decrypt(byte[] header, byte[] plaintext, byte[] nonce, byte[] mac) {
		CipherParameters cp = new KeyParameter(key);
		ParametersWithIV params = new ParametersWithIV(cp, nonce);

		byte[] headerMac = new byte[16];
		final Poly1305 poly1305 = new Poly1305();
		poly1305.init(cp);
		poly1305.update(header, 0, header.length);
		poly1305.doFinal(headerMac, 0);

		if (!Arrays.equals(headerMac, mac)) {
			throw new IllegalArgumentException("Auth failed");
		}

		StreamCipher engine = new XChaCha20Engine();
		engine.init(false, params);
		byte[] decrypted = new byte[plaintext.length];
		engine.processBytes(plaintext, 0, plaintext.length, decrypted, 0);
		return decrypted;
	}

	public byte[] open(byte[] token) {
		byte[] decoded = Base62.createInstance().decode(token);
		if (decoded[0] != VERSION) {
			throw new IllegalArgumentException("Not a valid version");
		}

		byte[] cypherText = Arrays.copyOfRange(decoded, HEADER_LENGTH, decoded.length);
		byte[] nonce = Arrays.copyOfRange(decoded, 5, decoded.length - cypherText.length);
		byte[] tag = Arrays.copyOfRange(decoded, decoded.length - TAG_LENGTH, decoded.length);
		byte[] header = Arrays.copyOfRange(decoded, 0, HEADER_LENGTH);
		byte[] decrypted = decrypt(header, cypherText, nonce, tag);
		return Arrays.copyOfRange(decrypted, 0, decrypted.length - 16);
	}
}
