package com.refoler.backend.commons.utils;

import com.refoler.backend.commons.service.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {
    public static void printDebug(String tag, String message) {
        if(Service.getInstance() != null && Service.getInstance().getArgument().isDebug) {
            print(tag, message);
        }
    }

    public static void print(String tag, String message) {
        String date = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS").format(new Date(System.currentTimeMillis()));
        System.out.printf("%s [%s] %s%n", date, tag, message);
    }
}
