package com.example.myapplication.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.myapplication.databinding.FragmentDashboardBinding;
import com.example.myapplication.services.WebSocketService;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private EditText serverAddressInput, portInput;
    private Button startButton;
    private TextView statusText;
    private WebSocketService webSocketService;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        serverAddressInput = binding.serverAddressInput;
        portInput = binding.portInput;
        startButton = binding.btnStart;
        statusText = binding.statusText;
        webSocketService = WebSocketService.getInstance();

        setupObservers();
        setupButtonListener();

        return root;
    }

    private void setupObservers() {
        webSocketService.getIsStreaming().observe(getViewLifecycleOwner(), isStreaming -> {
            startButton.setText(isStreaming ? "STOP STREAMING" : "START STREAMING");
        });

        webSocketService.getConnectionStatus().observe(getViewLifecycleOwner(), statusText::setText);

        webSocketService.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtonListener() {
        startButton.setOnClickListener(v -> {
            String serverAddress = serverAddressInput.getText().toString().trim();
            String port = portInput.getText().toString().trim();

            if (webSocketService.getIsStreaming().getValue() != null && webSocketService.getIsStreaming().getValue()) {
                webSocketService.disconnect();
            } else {
                if (serverAddress.isEmpty() || port.isEmpty()) {
                    Toast.makeText(requireContext(), "Server address or port cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                webSocketService.connect(serverAddress, port);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}