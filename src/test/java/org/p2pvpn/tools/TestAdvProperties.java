/*
    Copyright 2008, 2009 Wolfgang Ginolas

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

package test.org.p2pvpn.tools;

import java.security.KeyPair;
import java.security.PublicKey;
import org.junit.Before;
import org.junit.Test;
import org.p2pvpn.tools.AdvProperties;
import org.p2pvpn.tools.CryptoUtils;
import static org.junit.Assert.*;

public class TestAdvProperties {
	AdvProperties p;
	
	@Before public void before() {
		p = new AdvProperties();
		
		p.setProperty("a", "1");
		p.setProperty("b", "2");
		p.setProperty("c", "3");
	}
	
	@Test public void testStore() {
		assertEquals("a=1\nb=2\nc=3\n", p.toString(null, true, false));
	}
	
	@Test public void testSign() {
		KeyPair kp = CryptoUtils.createSignatureKeyPair();
		KeyPair kp2 = CryptoUtils.createSignatureKeyPair();
		p.sign("signature", kp.getPrivate());
		assertTrue(p.verify("signature", kp.getPublic()));
		assertFalse(p.verify("signature", kp2.getPublic()));
	}
	
	@Test public void testDecodeKey() {
		KeyPair kp = CryptoUtils.createSignatureKeyPair();
		p.setPropertyBytes("publicKey", kp.getPublic().getEncoded());
		p.sign("signature", kp.getPrivate());
		
		PublicKey key = CryptoUtils.decodeRSAPublicKey(p.getPropertyBytes("publicKey", null));
		assertTrue(p.verify("signature", key));
	}
	
}
