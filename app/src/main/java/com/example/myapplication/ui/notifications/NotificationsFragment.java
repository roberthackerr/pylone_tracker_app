package com.example.myapplication.ui.notifications;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.myapplication.databinding.FragmentNotificationsBinding;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private PreviewView previewView;
    private EditText serverAddressInput, portInput;
    private Button startButton;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private NotificationsViewModel viewModel;
    private static final int CAMERA_REQUEST_CODE = 101;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isFrameSending = false;
    private static final long FRAME_INTERVAL_MS = 1000; // Send frame every 1 second

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
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        previewView = binding.previewView;
        serverAddressInput = binding.serverAddressInput;
        portInput = binding.portInput;
        startButton = binding.btnStart;
        cameraExecutor = Executors.newSingleThreadExecutor();

        viewModel = new ViewModelProvider(this).get(NotificationsViewModel.class);

        setupObservers();
        setupButtonListener();
        checkPermissionsAndStartCamera();

        return root;
    }

    private void setupObservers() {
        viewModel.getIsStreaming().observe(getViewLifecycleOwner(), isStreaming -> {
            startButton.setText(isStreaming ? "STOP STREAMING" : "START STREAMING");
        });

        viewModel.getConnectionStatus().observe(getViewLifecycleOwner(), status -> {
            // Optional: Display status in a TextView or Toast if needed
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtonListener() {
        startButton.setOnClickListener(v -> {
            String serverAddress = serverAddressInput.getText().toString().trim();
            String port = portInput.getText().toString().trim();

            if (viewModel.getIsStreaming().getValue() != null && viewModel.getIsStreaming().getValue()) {
                viewModel.stopStreaming();
            } else {
                if (serverAddress.isEmpty() || port.isEmpty()) {
                    Toast.makeText(requireContext(), "Server address or port cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.startStreaming(serverAddress, port);
            }
        });
    }

    private void checkPermissionsAndStartCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (viewModel.getIsStreaming().getValue() != null && viewModel.getIsStreaming().getValue() && !isFrameSending) {
                        isFrameSending = true;
                        viewModel.sendFrame(imageProxy);
                    }
                    imageProxy.close();
                });

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
        handler.post(frameSendRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        handler.removeCallbacks(frameSendRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        viewModel.stopStreaming();
        handler.removeCallbacks(frameSendRunnable);
        binding = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        }
    }
}