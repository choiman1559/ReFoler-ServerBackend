package com.refoler.backend.llm.role;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.text.CharacterIterator;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("unused")
public class CommonTools {
    @Tool("Converts unix time value to human-readable format")
    public String unixTimeToHumanReadable(@P("unix time value to convert") long unixTime) {
        return new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new Date(unixTime));
    }

    @Tool("Converts human-readable format value to unix time")
    public long humanReadableToUnixTime(@P("Human-readable value to convert") String humanReadable) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault());
        Date date = sdf.parse(humanReadable);
        return date.getTime();
    }

    @Tool("Converts byte size value to human-readable format")
    public String humanReadableByteCountBin(@P("Byte size value to convert") long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return "%s B".formatted(bytes);
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format(Locale.getDefault(), "%.1f %ciB", value / 1024.0, ci.current());
    }

    @Tool("Converts human-readable byte count bin value to raw bytes format")
    public long rawByteCountFromHumanReadable(@P("Human readable size to convert") String humanReadable) {
        humanReadable = humanReadable.trim();
        String[] parts = humanReadable.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid human-readable format");
        }
        double value = Double.parseDouble(parts[0]);
        String unit = parts[1].toUpperCase();
        long multiplier = switch (unit) {
            case "B", "BYTES" -> 1;
            case "KI", "KIB" -> 1024;
            case "MI", "MIB" -> 1024 * 1024;
            case "GI", "GIB" -> 1024 * 1024 * 1024;
            case "TI", "TIB" -> 1024L * 1024 * 1024 * 1024;
            case "PI", "PIB" -> 1024L * 1024 * 1024 * 1024 * 1024;
            case "EI", "EIB" -> 1024L * 1024 * 1024 * 1024 * 1024 * 1024;
            default -> throw new IllegalArgumentException("Unknown unit: %s".formatted(unit));
        };
        return (long) (value * multiplier);
    }
}
