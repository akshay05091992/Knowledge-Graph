package org.aksw.avatar;

import java.util.List;

public class ListUtil {
	
	public static <T> boolean isNotEmpty(List<T> list) {
		if (list != null) {
			if (!list.isEmpty()) {
				return true;
			}
		}
		return false;
	}

}
