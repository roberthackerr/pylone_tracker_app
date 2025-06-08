package com.example.myapplication.ui.notifications;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.myapplication.databinding.FragmentNotificationsBinding;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.example.myapplication.services.WebSocketService;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private PreviewView previewView;
    private Button startButton;
    private TextView statusText;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private WebSocketService webSocketService;
    private static final int CAMERA_REQUEST_CODE = 101;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isFrameSending = false;
    private static final long FRAME_INTERVAL_MS = 1000;

    private final Runnable frameSendRunnable = new Runnable() {
        @Override
        public void run() {
            isFrameSending = false;
            handler.postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        Log.d("NotificationsFragment", "onCreateView called");
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        previewView = binding.previewView;
        startButton = binding.btnStart;
        statusText = binding.statusText; // Add this if statusText is in layout
        cameraExecutor = Executors.newSingleThreadExecutor();
        webSocketService = WebSocketService.getInstance();

        setupObservers();
        setupButtonListener();
        checkPermissionsAndStartCamera();

        return root;
    }

    private void setupObservers() {
        webSocketService.getIsStreaming().observe(getViewLifecycleOwner(), isStreaming -> {
            startButton.setText(isStreaming ? "STOP STREAMING" : "START STREAMING");
            Log.d("NotificationsFragment", "Streaming state: " + isStreaming);
        });

        webSocketService.getConnectionStatus().observe(getViewLifecycleOwner(), status -> {
            if (statusText != null) {
                statusText.setText(status);
            }
            Log.d("NotificationsFragment", "Connection status: " + status);
        });

        webSocketService.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                Log.e("NotificationsFragment", "Error: " + error);
            }
        });
    }

    private void setupButtonListener() {
        startButton.setOnClickListener(v -> {
            startCamera();
        });
    }

    private void checkPermissionsAndStartCamera() {
        int permissionStatus = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA);
        Log.d("Permissions", "CAMERA permission status: " + (permissionStatus == PackageManager.PERMISSION_GRANTED ? "Granted" : "Not Granted"));
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            Log.d("Permissions", "Starting camera");
            startCamera();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.CAMERA)) {
            Log.d("Permissions", "Showing rationale for CAMERA permission");
            Toast.makeText(requireContext(), "Camera permission is needed to stream video", Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            Log.d("Permissions", "Requesting CAMERA permission");
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        }
    }

    private void startCamera() {
        Log.d("Camera", "Starting CameraX");
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Log.d("Camera", "CameraProvider initialized");

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                Log.d("Camera", "Preview set");

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (webSocketService.getIsStreaming().getValue() != null && webSocketService.getIsStreaming().getValue() && !isFrameSending) {
                        isFrameSending = true;
                        Log.d("Camera", "Sending frame to WebSocketService");
                        webSocketService.sendCameraFrame(imageProxy);
                    }
                    imageProxy.close();
                });
                Log.d("Camera", "ImageAnalysis set");

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                if (!cameraProvider.hasCamera(cameraSelector)) {
                    Log.w("Camera", "No back camera available");
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "No camera available", Toast.LENGTH_SHORT).show());
                    return;
                }

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                Log.d("Camera", "Camera bound to lifecycle");

            } catch (Exception e) {
                Log.e("Camera", "Camera error: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("NotificationsFragment", "onResume called");
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d("Permissions", "Starting camera in onResume");
            startCamera();
        }
        handler.post(frameSendRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("NotificationsFragment", "onPause called");
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        handler.removeCallbacks(frameSendRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d("NotificationsFragment", "onDestroyView called");
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        handler.removeCallbacks(frameSendRunnable);
        binding = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("Permissions", "CAMERA permission granted");
            startCamera();
        } else {
            Log.d("Permissions", "CAMERA permission denied");
            if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.CAMERA)) {
                Toast.makeText(requireContext(), "Camera permission permanently denied. Enable it in Settings.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                startActivity(intent);
            } else {
                Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show();
            }
            requireActivity().getSupportFragmentManager().popBackStack();
        }
    }
}