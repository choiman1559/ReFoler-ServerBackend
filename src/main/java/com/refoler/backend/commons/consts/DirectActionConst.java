package com.refoler.backend.commons.consts;

public class DirectActionConst {
    public static final String SERVICE_TYPE_FILE_ACTION = "service_type_file_action";
    public static final String ACTION_SIDE_REQUESTER = "requester";
    public static final String ACTION_SIDE_RESPONSER = "responser";

    public static final String KEY_ACTION_SIDE = "action_side";
    public static final String KEY_ACTION_TYPE = "action_type";
    public static final String KEY_REQUEST_PACKET = "request_packet";
    public static final String KEY_RESPONSE_PACKET = "response_packet";

    public static final String RESULT_OK = "ok";
    public static final String RESULT_ERROR_UNKNOWN = "error_unknown";
    public static final String RESULT_ERROR_EXCEPTION = "error_exception";
    public static final String RESULT_ERROR_DENIED_BY_RESPONSER = "error_denied_by_responser";
    public static final String RESULT_ERROR_NOT_IMPLEMENTED = "error_not_implemented";

    public static String getErrorCode(String code, String message) {
        return String.format("%s:%s", code, message);
    }
}