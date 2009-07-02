/*
    Copyright 2009 Wolfgang Ginolas

    This file is part of P2PVPN.

    P2PVPN is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Foobar is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.p2pvpn.tools;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Utility class to create differenty cryptographic algorithms.
 * @author wolfgang
 */
public class CryptoUtils {
	
	private static final int RSA_KEYSIZE = 1024;
	
	static {
		initBC();
	}

	/**
	 * Initialize the bouncy castle provider.
	 */
	static private void initBC() {
		Security.addProvider(new BouncyCastleProvider());
	}

	/**
	 * @return a SecureRandom object
	 */
	static public SecureRandom getSecureRandom() {
		try {
			return SecureRandom.getInstance("SHA1PRNG");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}
	}

	/**
	 * @return a Signature object
	 */
	static public Signature getSignature() {
		try {
			return Signature.getInstance("SHA1withRSA", "BC");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}
	}

	/**
	 * @return a new KeyPair usable to sign things
	 */
	static public KeyPair createSignatureKeyPair() {
		try {
			KeyPairGenerator g = KeyPairGenerator.getInstance("RSA", "BC");
			g.initialize(RSA_KEYSIZE, getSecureRandom());
			return g.generateKeyPair();
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}
	}

	/**
	 * @return a asymmetric Cipher
	 */
	static public Cipher getAsymmetricCipher() {
		try {
			return Cipher.getInstance("RSA/NONE/PKCS1Padding", "BC");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}
	}

	/**
	 * @return a KeyPair that cna be used or an asymmetric cipher
	 */
	static public KeyPair createEncryptionKeyPair() {
		return createSignatureKeyPair();		// also uses RSA
	}

	/**
	 * Convert a byte array to a RSA public key
	 * @param ekey the byte array
	 * @return the public key
	 */
	static public PublicKey decodeRSAPublicKey(byte[] ekey) {
		try {
			X509EncodedKeySpec spec = new X509EncodedKeySpec(ekey);
			KeyFactory factory = KeyFactory.getInstance("RSA", "BC");
			return (RSAPublicKey) factory.generatePublic(spec);
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}
	}

	/**
	 * Convert a byte array to a RSA private key
	 * @param ekey the byte array
	 * @return the private key
	 */
	static public PrivateKey decodeRSAPrivateKey(byte[] ekey) {
		try {
			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(ekey);
			KeyFactory factory = KeyFactory.getInstance("RSA", "BC");
			return (RSAPrivateKey) factory.generatePrivate(spec);
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}
	}

	/**
	 * @return a hash algorithm
	 */
	static public MessageDigest getMessageDigest() {
		try {
			return MessageDigest.getInstance("SHA1");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}		
	}

	/**
	 * @return a symmetric cipher
	 */
	static public Cipher getSymmetricCipher() {
		try {
			return Cipher.getInstance("AES/CBC/ISO10126Padding", "BC");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}			
	}

	/**
	 * @return the key length of the symmetric cipher in bytes
	 */
	static public int getSymmetricKeyLength() {
		return 16;
	}

	/**
	 * Create a symmetric key from bytes.
	 * @param b the bytes
	 * @return the symmetric key
	 */
	static public SecretKey decodeSymmetricKey(byte[] b) {
		return new SecretKeySpec(b, 0, 16, "AES");
	}
}
