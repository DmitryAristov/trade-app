package org.bybittradeapp.logging;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.bybittradeapp.Main.LOG_LEVEL;

public class Log {
    public enum Level {
        DEBUG,
        INFO
    }

    public static final long PROGRESS_LOG_STEP = 30000L;
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String LOG_FILE_PATH = System.getProperty("user.dir") + "/src/main/resources/logs/output.log";

    public static void debug(String message) {
        log(message, Level.DEBUG);
    }

    public static void info(String message) {
        log(message, Level.INFO);
    }

    public static void debug(String message, Instant time) {
        log(message, time, Level.DEBUG);
    }

    public static void info(String message, Instant time) {
        log(message, time, Level.INFO);
    }

    private static void log(String message, Instant time, Level level) {
        String classAndMethodName = getClassAndMethod();
        LocalDateTime time_ = LocalDateTime.ofInstant(time, ZoneOffset.UTC);
        String logEntry = level + ((level == Level.INFO) ? " " : "") +
                " [" + LocalDateTime.now().format(DATETIME_FORMATTER) + "] " +
                classAndMethodName +
                message +
                " on " + time_.format(DATETIME_FORMATTER);

        if ((level == Level.DEBUG && LOG_LEVEL == Level.DEBUG) || level == Level.INFO) {
            System.out.println(logEntry);
        }
        writeLogFile(logEntry, level, classAndMethodName);
    }

    private static void log(String message, Level level) {
        String classAndMethodName = getClassAndMethod();
        String logEntry = level + ((level == Level.INFO) ? " " : "") +
                " [" + LocalDateTime.now().format(DATETIME_FORMATTER) + "] " +
                classAndMethodName +
                message;

        if ((level == Level.DEBUG && LOG_LEVEL == Level.DEBUG) || level == Level.INFO) {
            System.out.println(logEntry);
        }
        writeLogFile(logEntry, level, classAndMethodName);
    }

    private static void writeLogFile(String logEntry, Level level, String classAndMethodName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE_PATH, true))) {
            writer.write(logEntry);
            writer.newLine();
        } catch (IOException e) {
            String message = "writeLogFile throws exception: " +
                    e.getMessage() + "\n" +
                    Arrays.toString(e.getStackTrace());
            String errorEntry = level + " [" + LocalDateTime.now().format(DATETIME_FORMATTER) + "] " + classAndMethodName + message;
            System.out.println(errorEntry);
            throw new RuntimeException(e);
        }
    }

    public static void logProgress(
            long startTime,
            AtomicLong step,
            double progress,
            String processName
    ) {
        if (progress == 0) {
            return;
        }
        if (Instant.now().toEpochMilli() - startTime > step.get()) {
            LocalDateTime startTime_ = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.of("+3"));
            String formatted = startTime_.plusSeconds(
                    Math.round((LocalDateTime.now().toEpochSecond(ZoneOffset.of("+3")) -
                            startTime_.toEpochSecond(ZoneOffset.of("+3"))) / (progress))
            ).format(DATETIME_FORMATTER);

            Log.info(String.format("operation '%s' progress %.2f%%. Will done ~ %s", processName, progress * 100, formatted));
            step.addAndGet(PROGRESS_LOG_STEP);
        }
    }

    private static String getClassAndMethod() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        int i = 1;
        String fullClassName = stackTrace[i].getClassName();
        while (fullClassName.equals(Log.class.getName())) {
            i++;
            fullClassName = stackTrace[i].getClassName();
        }
        String methodName = stackTrace[i].getMethodName();
        String simpleClassName = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
        return simpleClassName + "." + methodName + " :::: ";
    }
}
