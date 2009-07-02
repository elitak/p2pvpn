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

package org.p2pvpn.tools;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This is a Map with the version. The version is increased with every change made.
 * @author Wolfgang Ginolas
 */
public class VersionizedMap<K, V> implements Map<K, V>, Serializable {

	private Map<K, V> map;

	private long version;
	
	public VersionizedMap(Map<K, V> map) {
		this.map = map;
		version = 0;
	}
	
	public VersionizedMap() {
		this(new HashMap<K, V>());
	}

	public long getVersion() {
		return version;
	}
	
	public int size() {
		return map.size();
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	public boolean containsValue(Object val) {
		return map.containsValue(val);
	}

	public V get(Object key) {
		return map.get(key);
	}

	public V put(K key, V val) {
		version++;
		return map.put(key, val);
	}

	public V remove(Object key) {
		version++;
		return map.remove(key);
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		version++;
		map.putAll(m);
	}

	public void clear() {
		version++;
		map.clear();
	}

	public Set<K> keySet() {
		return map.keySet();
	}

	public Collection<V> values() {
		return map.values();
	}

	public Set<Entry <K, V>> entrySet() {
		return map.entrySet();
	}

}
