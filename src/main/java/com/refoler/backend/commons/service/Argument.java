package com.refoler.backend.commons.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

@SuppressWarnings("SameParameterValue")
public class Argument {

    public int port;
    public String host;
    public boolean isDebug;
    public String version;

    // Select Switch between DBMS/Endpoint mode
    public static final int OPERATION_MODE_ENDPOINT = 0;
    public static final int OPERATION_MODE_DBMS = 1;
    public static final int OPERATION_MODE_LLM = 2;
    public int operationMode;
    public boolean webSocketEnabled;

    // For DBMS Node access for Endpoint & LLM Node
    public int recordNodePort;
    public String recordNodeHost;

    // For Endpoint Operation Mode
    public int llmNodePort;
    public String llmNodeHost;
    public boolean useAuthentication;
    public String authCredentialPath;

    // For LLM Operation Mode
    public String llmServerEndpoint;
    public String bigModelName;
    public String littleModelName;
    public String openAiTokenKey;

    // For DBMS Operation Mode (+ LLM Caching)
    public String recordDirectoryPath;
    public long recordHotRecordLifetime;
    public long recordGcInterval;

    public static Argument buildFrom(List<String> argument) throws IOException, IllegalArgumentException {
        if(argument.isEmpty()) {
            throw new IllegalArgumentException("argument is not found!");
        }

        File file = new File(argument.getFirst());
        if(file.exists() && file.canRead()) {
            return (Argument) parsePropertiesFromFile(file.getPath(), Argument.class);
        } else {
            throw new FileNotFoundException("com.noti.server.process.Argument File not found or Not Accessible");
        }
    }

    private static Object parsePropertiesFromFile(String filePath, Class<?> cls) throws IOException {
        Properties fileProps = new Properties();
        fileProps.load(new FileInputStream(filePath));

        final ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(fileProps, cls);
    }
}
