package com.example.myapplication.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.databinding.FragmentHomeBinding;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;


public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private TextView cellInfoText;
    private final Handler handler = new Handler();
    private final int INTERVAL_MS = 1000; // 1000 milliseconds = 1 second
    private final Runnable fetchCellInfoRunnable = new Runnable() {
        @Override
        public void run() {
            getCellData(); // Your method to fetch cell info
            handler.postDelayed(this, INTERVAL_MS); // Schedule next run
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        handler.post(fetchCellInfoRunnable); // Start repeating
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(fetchCellInfoRunnable); // Stop repeating
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        cellInfoText = binding.textHome;
        checkPermissionsAndLoadData();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    private void checkPermissionsAndLoadData() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE}, 101);
        } else {
            getCellData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 101 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            getCellData();
        } else {
            Toast.makeText(getContext(), "Permissions required", Toast.LENGTH_SHORT).show();
        }
    }

    private void getCellData() {
        TelephonyManager tm = (TelephonyManager) requireContext().getSystemService(Context.TELEPHONY_SERVICE);
        int simCount = SubscriptionManager.from(requireContext()).getActiveSubscriptionInfoCountMax();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < simCount; i++) {
            int subId = getSubscriptionId(i);
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) continue;

            TelephonyManager tmSim = tm.createForSubscriptionId(subId);

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            List<CellInfo> cellInfos = tmSim.getAllCellInfo();
            if (cellInfos != null) {
                for (CellInfo cellInfo : cellInfos) {
                    if (cellInfo instanceof CellInfoLte && cellInfo.isRegistered()) {
                        CellIdentityLte identity = ((CellInfoLte) cellInfo).getCellIdentity();
                        CellSignalStrengthLte signal = ((CellInfoLte) cellInfo).getCellSignalStrength();

                        int pci = identity.getPci();
                        int tac = identity.getTac();
                        int ci = identity.getCi();
                        int band = identity.getEarfcn();
                        int rsrp = signal.getRsrp();
                        int rsrq = signal.getRsrq();

                        sb.append("SIM ").append(i + 1).append(":\n")
                                .append("PCI: ").append(pci).append("\n")
                                .append("TAC: ").append(tac).append("\n")
                                .append("CI: ").append(ci).append("\n")
                                .append("Band: ").append(band).append("\n")
                                .append("RSRP: ").append(rsrp).append("\n")
                                .append("RSRQ: ").append(rsrq).append("\n\n");
                    }
                }
            }
        }
        requireActivity().runOnUiThread(() -> cellInfoText.setText(sb.toString()));
    }
    @SuppressLint("MissingPermission")
    private int getSubscriptionId(int simSlotIndex) {
        SubscriptionManager sm = (SubscriptionManager) requireContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();

        if (subs != null) {
            for (SubscriptionInfo info : subs) {
                if (info.getSimSlotIndex() == simSlotIndex) {
                    return info.getSubscriptionId();
                }
            }
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }
}
