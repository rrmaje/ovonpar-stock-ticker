package crypto;

import org.bouncycastle.crypto.engines.ChaChaEngine;

/**
 * We want to use a 24 byte nonce because that's what the Branca standard calls for.
 */
class XChaCha20Engine extends ChaChaEngine {

    private final static int NONCE_SIZE_BYTES = 24;

    XChaCha20Engine() {
        super(20);
    }

    @Override
	public String getAlgorithmName() {
        return "XChaCha20";
    }

    @Override
	protected int getNonceSize() {
        return NONCE_SIZE_BYTES;
    }

}