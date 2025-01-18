package com.refoler.backend.commons.service;

import java.util.ArrayList;

public class ServiceStatusHandler {

    public interface ServiceStatusListener {
        void onServiceStart();
        void onServiceDead();
    }

    private final ArrayList<ServiceStatusListener> serviceStatusListeners;

    public ServiceStatusHandler() {
        serviceStatusListeners = new ArrayList<>();
    }

    public void addServiceListener(ServiceStatusListener serviceStatusListener) {
        this.serviceStatusListeners.add(serviceStatusListener);
    }

    public void onServiceStart() {
        for(ServiceStatusListener listener : serviceStatusListeners) {
            listener.onServiceStart();
        }
    }

    public void onServiceDead() {
        for(ServiceStatusListener listener : serviceStatusListeners) {
            listener.onServiceDead();
        }
    }
}
