package com.refoler.backend.commons.consts;

public class LlmConst {
    public static final String ERROR_CONVERSATION_SESSION_NOT_INITIALIZED = "session_not_initialized";
    public static final String ERROR_CONVERSATION_MESSAGE_NOT_REGISTERED = "conversation_not_ignited";
    public static final String ERROR_CONVERSATION_ALREADY_PROCESSED = "conversation_already_processed";
    public static final String ERROR_ANOTHER_CONVERSATION_PROCESSING = "another_conversation_processing";

    public static final String RAW_DATA_END_OF_CONVERSATION = "$conversation_terminated;";
    public static final String RAW_DATA_ERROR_THROWN = "$exception_thrown;";
    public static final String RAW_DATA_LINE_SEPARATION = "$new_line;";
    public static final String RAW_DATA_SPACE = "$space;";
}
