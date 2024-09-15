package org.bybittradeapp.logging;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

public class Log {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void log(String message) {
        LocalDateTime now = LocalDateTime.now();
        String formattedDateTime = now.format(formatter);

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String fullClassName = stackTrace[2].getClassName();
        String methodName = stackTrace[2].getMethodName();

        String simpleClassName = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);

        System.out.println("[" + formattedDateTime + "] {" + simpleClassName + "." + methodName + "}:::: " + message);
    }

    public static void logProgress(
            long startTime,
            AtomicLong step,
            double progress,
            String processName
    ) {
        if (Instant.now().toEpochMilli() - startTime > step.get()) {
            LocalDateTime startTime_ = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.of("+3"));
            String formatted = startTime_.plusSeconds(
                    Math.round((LocalDateTime.now().toEpochSecond(ZoneOffset.of("+3")) -
                            startTime_.toEpochSecond(ZoneOffset.of("+3"))) / (progress))
            ).format(formatter);

            Log.log(String.format("operation '%s' progress %.2f%%. Will done ~ %s", processName, progress * 100, formatted));
            step.addAndGet(30000L);
        }
    }
}
