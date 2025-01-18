package com.refoler.backend.commons.utils;

import kotlinx.io.Buffer;
import kotlinx.io.BuffersJvmKt;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class IOUtils {
    public static boolean createNewFile(File destFile) throws IOException {
        return (destFile.exists() & destFile.delete()) & destFile.createNewFile();
    }

    public static String readFrom(File dest) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             Buffer fileBuffer = new Buffer();
             FileInputStream fileInputStream = new FileInputStream(dest)) {

            BuffersJvmKt.readTo(BuffersJvmKt.transferFrom(fileBuffer, fileInputStream), outputStream, dest.length());
            return outputStream.toString();
        }
    }

    public static void writeTo(File dest, String data) throws IOException {
        writeTo(dest, data, false);
    }

    public static void writeTo(File dest, String data, boolean overwriteExists) throws IOException {
        if (overwriteExists) {
            createNewFile(dest);
        }

        byte[] dataArray = data.getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(dataArray);
             Buffer fileBuffer = new Buffer();
             FileOutputStream fileOutputStream = new FileOutputStream(dest)) {

            BuffersJvmKt.readTo(BuffersJvmKt.transferFrom(fileBuffer, inputStream), fileOutputStream, dataArray.length);
        }
    }
}