package com.luukien.javacard.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

public class EnvLoader {
    private static final Map<String, String> env;

    static {
        try {
            env = Files.lines(Paths.get(".env"))
                    .filter(line -> line.contains("="))
                    .map(line -> line.split("=", 2))
                    .collect(Collectors.toMap(
                            arr -> arr[0].trim(),
                            arr -> arr.length > 1 ? arr[1].trim() : ""
                    ));
        } catch (IOException e) {
            throw new RuntimeException("Không load được file .env", e);
        }
    }

    public static String get(String key) {
        return System.getenv(key) != null ? System.getenv(key) : env.get(key);
    }
}
