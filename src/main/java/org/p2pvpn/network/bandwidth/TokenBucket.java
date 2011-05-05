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

package org.p2pvpn.network.bandwidth;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class can be used to limit bandwidth using the token bucket algorithm.
 * @author Wolfgang Ginolas
 */
public class TokenBucket {

	private static final double MIN_WAIT_S = 0.005;
	private static final double MAX_WAIT_S = 0.1;

	private static final long TIMEOUT = 1000;

	private double bandwidth;
	private double bucketSize;

	private double bucket;
	private long lastFill;

	/**
	 * Create a new TokenBicket
	 * @param bandwidth the maximum andwith in tokens/s
	 * @param bucketSize the maximum burst size
	 */
	public TokenBucket(double bandwidth, double bucketSize) {
		this.bandwidth = bandwidth;
		this.bucketSize = bucketSize;

		bucket = 0;
		lastFill = System.currentTimeMillis();
	}

	/**
	 * Update the bucket fill level.
	 */
	private synchronized void updateBucket() {
		long time = System.currentTimeMillis();

		if (bandwidth==0) {
			bucket = bucketSize;
		} else {
			bucket += bandwidth * (time-lastFill) / 1000;
			if (bucket > bucketSize) bucket = bucketSize;
		}
		lastFill = time;
	}

	/**
	 * Wait until this many tokens may be send/received.
	 * @param tokens number if tokens
	 */
	public void waitForTokens(double tokens) {
		while (true) {
			updateBucket();
			synchronized (this) {
				if (bucket>=0) break;
			}
			double waitTime = -bucket / bandwidth;
			if (waitTime < MIN_WAIT_S || waitTime > MAX_WAIT_S) waitTime = MIN_WAIT_S;
			try {
				Thread.sleep((long) (waitTime*1000));
			} catch (InterruptedException ex) {
				Logger.getLogger(TokenBucket.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
		synchronized (this) {
			bucket -= tokens;
		}
	}

	/**
	 * Check if the number of tokens may be send/received right now.
	 * @param tokens number of tokens
	 * @return send/received allowed right now?
	 */
	public boolean tokensAvailable(double tokens) {
		updateBucket();
		synchronized (this) {
			if (bucket>=0) {
				bucket -= tokens;
				return true;
			} else {
				return false;
			}
		}
	}

	/**
	 * Set the maximum bandwidth.
	 * @param bandwidth the bandwidth in tokens/s
	 */
	public synchronized void setBandwidth(double bandwidth) {
		this.bandwidth = bandwidth;
	}
}
