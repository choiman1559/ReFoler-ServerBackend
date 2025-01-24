package com.refoler.backend.llm.role;

import com.refoler.Refoler;
import io.ktor.server.application.ApplicationCall;

public class UserSession {
    final String uid;
    Refoler.Device[] deviceList;

    public UserSession(String uid) {
        this.uid = uid;
    }

    public void igniteConversation(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) {

    }
}
