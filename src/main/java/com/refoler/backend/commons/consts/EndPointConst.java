package com.refoler.backend.commons.consts;

public class EndPointConst {
    public final static String SERVICE_TYPE_LLM = "type_llm_conversation";
    public final static String SERVICE_TYPE_FCM_POST = "type_fcm_message";
    public final static String SERVICE_TYPE_CHECK_ALIVE = "type_check_alive";
    public final static String SERVICE_TYPE_TRANSFER_FILE_PART = "type_transfer_file_part";

    public final static String ERROR_ILLEGAL_AUTHENTICATION = "server_illegal_authentication";
    public final static String ERROR_FCM_CODE_NOT_AVAILABLE = "fcm_code_not_available";
    public final static String ERROR_RAW_ANOTHER_SESSION_STARTED = "$error_another_session_started";
    public final static String ERROR_RAW_SESSION_EXPIRED_TIMEOUT = "$error_session_expired_timeout";

    public final static String KEY_AUTHENTICATION = "Authorization";
    public final static String KET_UID = "Authorization-Uid";
    public final static String KEY_EXTRA_DATA = "extra_data";

    public final static String FILE_PART_CONTROL_PREFIX = "␆␁␅␁␅";
    public final static String FILE_PART_CONTROL_SEPARATOR = "␆␁␁␂";
    public final static String FILE_PART_CONTROL_HEADER_ACK = "$header_ack";
    public final static String FILE_PART_CONTROL_HEADER_OK = "$header_ok";
    public final static String FILE_PART_CONTROL_PEER_ACK = "$peer_ack";
    public final static String FILE_PART_CONTROL_PEER_DISCONNECTED = "$peer_disconnected";

    public final static String PREFIX_FCM_PROXY = "$fcm_proxy=%s";
    public final static String FCM_KEY_SENT_DEVICE = "sent_device";
    public final static String FCM_KEY_DATA_HASH = "data_hash";
    public final static String FCM_KEY_SENT_DATE = "sent_date";
    public final static String FCM_KEY_LIST_TARGETS = "targets";
    public final static String FCM_KEY_IS_AGENT = "is_agent";
}
