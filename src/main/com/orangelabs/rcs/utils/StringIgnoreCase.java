package com.orangelabs.rcs.utils;

import java.util.Locale;

public class StringIgnoreCase {
	
	public static boolean equals(String one, String other) {
		
		return one == other ? other != null : one.equalsIgnoreCase(other);
	}
	
	public static boolean emptyOrNull(String one) {
		return one == null || one.isEmpty() || one.trim().isEmpty();
	}
	
	public static boolean startsWith(String one, String other) {
		if (one == other) return other != null;
		
		return one.toLowerCase(Locale.ENGLISH).startsWith(other);
	}

	public static boolean contains(String one, String other) {
		if (one == other) return other != null;
		
		return one.toLowerCase(Locale.ENGLISH).contains(other);
	}
}
