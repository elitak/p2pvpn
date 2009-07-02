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

import java.util.LinkedList;
import java.util.Queue;

/**
 * A claas that cna calculate a liding average.
 * @author Wolfgang Ginolas
 */
public class SlidingAverage {
	private int bucketLen;
	private Queue<Double>  buckets;
	private double bucketSum;

	/**
	 * Create a new Sliding average.
	 * @param bucketLen number of values that are used for the average
	 * @param init initialisation value
	 */
	public SlidingAverage(int bucketLen, double init) {
		this.bucketLen = bucketLen;

		buckets = new LinkedList<Double>();
		for(int i=0; i<bucketLen; i++) buckets.add(init);
		bucketSum = bucketLen*init;
	}

	/**
	 * Use this value for the average.
	 * @param val the value
	 */
	public void putVaule(double val) {
		long time = System.currentTimeMillis();
		bucketSum -= buckets.poll();
		buckets.offer(val);
		bucketSum += val;
	}

	public double getAverage() {
		return bucketSum/bucketLen;
	}
}
