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

/**
 * This class can measure the bandwidth of a connection.
 * @author Wolfgang Ginolas
 */
public class MeasureBandwidth {
	private long bucketTime;
	private int bucketLen;
	private int currentBucket;
	private long lastShift;
	private SlidingAverage avg;


	/**
	 * Create a new MeasureBandwidth
	 * @param bucketTime number of steps that are used for the average
	 * @param bucketLen the time for one step. bucketTime*bucketLen gives the time of the sliding average
	 */
	public MeasureBandwidth(double bucketTime, int bucketLen) {
		this.bucketTime = (long)(bucketTime*1000);
		this.bucketLen = bucketLen;

		avg = new SlidingAverage(bucketLen, 0);

		currentBucket = 0;
		lastShift = System.currentTimeMillis();
	}

	/**
	 * Check if the buckets need to be shifted.
	 */
	private void shift() {
		long time = System.currentTimeMillis();
		int cnt = 0;
		while (
			lastShift + bucketTime < time) {
			avg.putVaule(currentBucket);
			currentBucket = 0;
			lastShift += bucketTime;

			if (cnt>bucketLen) lastShift = time;
			cnt++;
		}
	}

	/**
	 * Count bytes that were just received/send.
	 * @param size nimber of bytes
	 */
	public void countPacket(int size) {
		shift();
		currentBucket += size;
	}

	/**
	 * Return the current average bandwidth.
	 * @return the bandwidth in bytes/s
	 */
	public double getBandwidth() {
		shift();
		return avg.getAverage() / bucketTime * 1000;
	}
}
