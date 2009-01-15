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

public class CryptoUtils {
	
	private static final int RSA_KEYSIZE = 1024;
	
	static {
		initBC();
	}
	
	static private void initBC() {
		Security.addProvider(new BouncyCastleProvider());
	}
	
	static public SecureRandom getSecureRandom() {
		try {
			return SecureRandom.getInstance("SHA1PRNG");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}
	}
	
	static public Signature getSignature() {
		try {
			return Signature.getInstance("SHA1withRSA", "BC");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}
	}
	
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
	
	static public Cipher getAsymmetricCipher() {
		try {
			return Cipher.getInstance("RSA/NONE/PKCS1Padding", "BC");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}
	}
	
	static public KeyPair createEncryptionKeyPair() {
		return createSignatureKeyPair();		// also uses RSA
	}
	
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
	
	static public MessageDigest getMessageDigest() {
		try {
			return MessageDigest.getInstance("SHA1");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}		
	}
	
	static public Cipher getSymmetricCipher() {
		try {
			return Cipher.getInstance("AES/CBC/ISO10126Padding", "BC");
		} catch (Throwable t) {
			Logger.getLogger("").log(Level.SEVERE, null, t);
			assert false;
			return null;
		}			
	}
	
	static public int getSymmetricKeyLength() {
		return 16;
	}
	
	static public SecretKey decodeSymmetricKey(byte[] b) {
		return new SecretKeySpec(b, 0, 16, "AES");
	}
}
