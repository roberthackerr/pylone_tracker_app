package com.example.myapplication.services;

import android.util.Base64;
import android.util.Log;
import androidx.camera.core.ImageProxy;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.BitmapFactory;

public class WebSocketService {
    private static WebSocketService instance;
    private final OkHttpClient client;
    private WebSocket data1WebSocket; // For /ws/data1 (primary_cell)
    private WebSocket data2WebSocket; // For /ws/data2 (neighboring_cells)
    private WebSocket imageWebSocket; // For /ws/image (camera frames)
    private String serverAddress;
    private String port;
    private final MutableLiveData<Boolean> isStreaming = new MutableLiveData<>(false);
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<>("Disconnected");
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private WebSocketService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized WebSocketService getInstance() {
        if (instance == null) {
            instance = new WebSocketService();
        }
        return instance;
    }

    public LiveData<Boolean> getIsStreaming() {
        return isStreaming;
    }

    public LiveData<String> getConnectionStatus() {
        return connectionStatus;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void connect(String serverAddress, String port) {
        if (Boolean.TRUE.equals(isStreaming.getValue())) {
            return; // Already connected
        }

        if (serverAddress.trim().isEmpty() || port.trim().isEmpty()) {
            errorMessage.postValue("Server address or port cannot be empty");
            return;
        }

        this.serverAddress = serverAddress.trim();
        this.port = port.trim();

        // Connect to all three endpoints
        connectToEndpoint("data1", "/ws/data1");
        connectToEndpoint("data2", "/ws/data2");
        connectToEndpoint("image", "/ws/image");
    }

    private void connectToEndpoint(String endpointType, String path) {
        String url = "ws://" + serverAddress + ":" + port + path;
        Request request = new Request.Builder().url(url).build();

        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                synchronized (this) {
                    if (endpointType.equals("data1")) data1WebSocket = ws;
                    else if (endpointType.equals("data2")) data2WebSocket = ws;
                    else if (endpointType.equals("image")) imageWebSocket = ws;
                }
                isStreaming.postValue(true);
                connectionStatus.postValue("Connected to " + path);
                Log.d("WebSocketService", "Connected to " + url);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isStreaming.postValue(false);
                connectionStatus.postValue("Disconnected from " + path);
                errorMessage.postValue("Connection failed for " + path + ": " + t.getMessage());
                Log.e("WebSocketService", "Connection failed for " + url + ": " + t.getMessage());
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isStreaming.postValue(false);
                connectionStatus.postValue("Disconnected from " + path);
                Log.d("WebSocketService", "Disconnected from " + url + ": " + reason);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d("WebSocketService", "Received from " + path + ": " + text);
            }
        };

        client.newWebSocket(request, listener);
    }

    public void disconnect() {
        if (data1WebSocket != null) {
            data1WebSocket.close(1000, "User disconnected");
            data1WebSocket = null;
        }
        if (data2WebSocket != null) {
            data2WebSocket.close(1000, "User disconnected");
            data2WebSocket = null;
        }
        if (imageWebSocket != null) {
            imageWebSocket.close(1000, "User disconnected");
            imageWebSocket = null;
        }
        isStreaming.postValue(false);
        connectionStatus.postValue("Disconnected");
    }

    public void sendPrimaryCellData(String jsonData) {
        if (Boolean.TRUE.equals(isStreaming.getValue()) && data1WebSocket != null) {
            data1WebSocket.send(jsonData);
            Log.d("WebSocketService", "Sent primary cell data: " + jsonData.substring(0, Math.min(jsonData.length(), 50)) + "...");
        }
    }

    public void sendNeighboringCellData(String jsonData) {
        if (Boolean.TRUE.equals(isStreaming.getValue()) && data2WebSocket != null) {
            data2WebSocket.send(jsonData);
            Log.d("WebSocketService", "Sent neighboring cell data: " + jsonData.substring(0, Math.min(jsonData.length(), 50)) + "...");
        }
    }

    public void sendCameraFrame(ImageProxy imageProxy) {
        if (!Boolean.TRUE.equals(isStreaming.getValue()) || imageWebSocket == null) {
            return;
        }

        try {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            String base64 = bitmapToBase64(bitmap);
            imageWebSocket.send("data:image/jpeg;base64," + base64);
            Log.d("WebSocketService", "Sent camera frame to /ws/image");
        } catch (Exception e) {
            errorMessage.postValue("Image error: " + e.getMessage());
            Log.e("WebSocketService", "Image error: " + e.getMessage());
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, imageProxy.getWidth(), imageProxy.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 70, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
    }

    public void shutdown() {
        disconnect();
        client.dispatcher().executorService().shutdown();
    }
}