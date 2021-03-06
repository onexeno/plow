package com.breakersoft.plow.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

public final class PlowUtils {

    public static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        }
        catch (IllegalArgumentException e) {

        }
        return false;
    }

    public static boolean isValid(Collection<?> c) {
        if (c == null) {
            return false;
        }
        return !c.isEmpty();
    }

    public static boolean isValid(String s) {
        if (s == null) {
            return false;
        }
        return !s.isEmpty();
    }

    /**
     * Return true if the str is not null and not empty.
     * @param str
     * @return
     */
    public static String checkEmpty(String str) {
        if (str == null) {
            throw new NullPointerException("Expecting not null string");
        }
        if (str.isEmpty()) {
            throw new IllegalArgumentException("Expecting non-empty string.");
        }
        return str;
    }

    /**
     * Uniquify a collection of strings while maintaining order.
     * @param c
     * @return
     */
    public static String[] uniquify(Collection<String> c) {
        if (c == null) {
            return null;
        }
        return new LinkedHashSet<String>(c).toArray(new String[] {});
    }
}
