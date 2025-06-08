package com.example.myapplication.ui.notifications;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.example.myapplication.services.WebSocketService;

public class NotificationsViewModel extends ViewModel {

    private final WebSocketService webSocketService;

    public NotificationsViewModel() {
        webSocketService = WebSocketService.getInstance();
    }

    public LiveData<Boolean> getIsStreaming() {
        return webSocketService.getIsStreaming();
    }

    public LiveData<String> getConnectionStatus() {
        return webSocketService.getConnectionStatus();
    }

    public LiveData<String> getErrorMessage() {
        return webSocketService.getErrorMessage();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // No need to disconnect WebSocketService here, as it's shared across fragments
    }
}