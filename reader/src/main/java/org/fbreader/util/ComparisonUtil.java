
package org.fbreader.util;

import java.util.Objects;

public class ComparisonUtil {
	public static boolean equal(Object o1, Object o2) {
		return Objects.equals(o1, o2);
	}

	public static int hashCode(Object o) {
		return o != null ? o.hashCode() : 0;
	}
}
