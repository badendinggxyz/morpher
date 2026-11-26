package azbd.morpher.util;

import azbd.morpher.MorpherMod;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LogOnce {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private LogOnce() {}

    public static void warn(String key, String message, Throwable error) {
        if (REPORTED.add(key)) MorpherMod.LOGGER.warn(message, error);
    }
}
