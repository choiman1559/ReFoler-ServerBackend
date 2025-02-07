package com.refoler.backend.endpoint.provider;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.refoler.Refoler;
import com.refoler.backend.commons.consts.EndPointConst;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FirebaseHelper {

    public static ConcurrentHashMap<String, OauthToken> cachedTokenHashmap = new ConcurrentHashMap<>();

    public static void init(String credentialPath) throws IOException {
        FileInputStream serviceAccount = new FileInputStream(credentialPath);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();
        FirebaseApp.initializeApp(options);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean verifyToken(String idToken, String uid) {
        try {
            if (cachedTokenHashmap.containsKey(uid)) {
                OauthToken cachedToken = cachedTokenHashmap.get(uid);
                if (cachedToken.compareToken(idToken) && cachedToken.isStillValid()) {
                    return true;
                }
            }

            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            UserRecord userRecord = firebaseAuth.getUser(decodedToken.getUid());
            OauthToken oauthToken = new OauthToken(idToken);

            boolean isValid = uid.equals(userRecord.getUid());
            if (isValid) {
                cachedTokenHashmap.put(uid, oauthToken);
            }
            return isValid;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public static String postFcmMessage(Refoler.RequestPacket requestPacket) {
        try {
            Map<String, String> newMap = new HashMap<>();
            newMap.put(EndPointConst.KEY_EXTRA_DATA, requestPacket.getExtraData());

            Message message = Message.builder()
                    .putAllData(newMap)
                    .setTopic(requestPacket.getUid())
                    .build();
            return FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            return null;
        }
    }
}
