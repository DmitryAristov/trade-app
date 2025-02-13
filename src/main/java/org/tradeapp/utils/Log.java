package org.tradeapp.utils;

import org.tradeapp.ui.utils.TimeFormatter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import static org.tradeapp.backtest.constants.Settings.SYMBOL;
import static org.tradeapp.utils.Log.Level.*;

public class Log {

    public enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public static final String LOGS_DIR_PATH = System.getProperty("user.dir") + "/output/logs/";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    static {
        ensureLogDirectoryExists();
    }

    private String path = null;

    public Log() {  }

    public Log(String path) {
        this.path = path;
    }

    public void debug(String message) {
        log(message, DEBUG);
    }

    public void debug(String message, long mills) {
        log(message, DEBUG, mills);
    }

    public void info(String message) {
        log(message, INFO);
    }

    public void info(String message, long mills) {
        log(message, INFO, mills);
    }

    public void error(String message, long mills) {
        log(message, ERROR, mills);
    }

    public void warn(String message, long mills) {
        log(message, WARN, mills);
    }

    public RuntimeException throwError(String message, long mills) {
        log(message, ERROR, mills);
        return new RuntimeException(message);
    }

    public RuntimeException throwError(String message, Exception exception, long mills) {
        error(message, exception, mills);
        return new RuntimeException(exception);
    }

    public void error(String message, Exception exception, long mills) {
        log(exception.getClass() + ": " + message + ", caused by :: " +
                        exception.getMessage() +
                        Arrays.stream(exception.getStackTrace())
                                .map(StackTraceElement::toString)
                                .reduce("", (s1, s2) -> s1 + "\n    at " + s2),
                ERROR, mills);
    }

    public void logProgress(long startTime, AtomicLong step, double progress, String operationName, long mills) {
        if (progress == 0) {
            return;
        }
        if (Instant.now().toEpochMilli() - startTime > step.get()) {
            Instant approximateEndUTC = Instant.ofEpochMilli(startTime).plusMillis(
                    Math.round((Instant.now().toEpochMilli() - startTime) / progress));

            String logEntry = String.format("operation '%s' progress %.2f%%. Will done ~ %s", operationName, progress * 100,
                    TimeFormatter.format(approximateEndUTC));

            info(logEntry, mills);
            step.addAndGet(15_000L);
        }
    }

    public void removeLines(int count) {
        String dateSuffix = "_" + DATE_FORMAT.format(new Date()) + ".log";
        try (RandomAccessFile file = new RandomAccessFile(LOGS_DIR_PATH + path + dateSuffix, "rw")) {
            long length = file.length();
            int linesCount = 0;

            while (length > 0 && linesCount < count + 1) {
                length--;
                file.seek(length);
                if (file.readByte() == '\n') {
                    linesCount++;
                }
            }
            file.setLength(length + 1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void log(String message, Level level) {
        log(message, level, -1);
    }

    private void log(String message, Level level, long mills) {
        String classAndMethodName = getClassAndMethod();
        if (mills != -1) {
            String logEntry = "[" + TimeFormatter.now() + "] " + level + (level != DEBUG ? "  " : " ") + classAndMethodName + message + " on " + TimeFormatter.format(mills);
            writeLogFile(logEntry, mills);
            if (level != DEBUG)
                System.out.println(logEntry);
        }
    }

    private void writeLogFile(String logEntry, long mills) {
        ZonedDateTime zonedDateTime = Instant.ofEpochMilli(mills).atZone(ZoneId.of("UTC"));
        int year = zonedDateTime.getYear();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOGS_DIR_PATH + SYMBOL + "_" + year + "_tmp.log", true))) {
            writer.write(logEntry);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getClassAndMethod() {
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

    private static void ensureLogDirectoryExists() {
        File directory = new File(LOGS_DIR_PATH);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create log directory: " + LOGS_DIR_PATH);
            }
        }
    }
}
