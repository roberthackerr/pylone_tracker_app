package com.example.myapplication.ui.home;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.example.myapplication.services.WebSocketService;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import org.json.JSONObject;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private TextView cellInfoText;
    private WebSocketService webSocketService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int INTERVAL_MS = 3000; // Poll every 3 seconds
    private final Runnable fetchCellInfoRunnable = new Runnable() {
        @Override
        public void run() {
            getCellData();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        cellInfoText = binding.textHome;
        webSocketService = WebSocketService.getInstance();
        checkPermissionsAndLoadData();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(fetchCellInfoRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(fetchCellInfoRunnable);
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
            Toast.makeText(getContext(), "Permissions required for cell info", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint({"MissingPermission", "NewApi"})
    private void getCellData() {
        SubscriptionManager subscriptionManager = (SubscriptionManager) requireContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        TelephonyManager telephonyManager = (TelephonyManager) requireContext().getSystemService(Context.TELEPHONY_SERVICE);

        if (subscriptionManager == null || telephonyManager == null) {
            requireActivity().runOnUiThread(() -> cellInfoText.setText("SubscriptionManager or TelephonyManager not available"));
            return;
        }

        List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptionInfos == null || subscriptionInfos.isEmpty()) {
            requireActivity().runOnUiThread(() -> cellInfoText.setText("No active SIMs found"));
            return;
        }

        StringBuilder infoBuilder = new StringBuilder();
        JSONObject jsonData = new JSONObject();
        infoBuilder.append("Total SIM slots: ").append(subscriptionInfos.size()).append("\n\n");

        try {
            jsonData.put("type", "primary_cell");
            jsonData.put("sim_count", subscriptionInfos.size());
            JSONObject sims = new JSONObject();

            for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
                int subId = subscriptionInfo.getSubscriptionId();
                int simSlotIndex = subscriptionInfo.getSimSlotIndex();
                String carrierName = subscriptionInfo.getCarrierName().toString();
                String mcc = subscriptionInfo.getMccString();
                String mnc = subscriptionInfo.getMncString();

                infoBuilder.append("SIM ").append(simSlotIndex + 1).append(" (").append(carrierName).append("):\n");
                Log.d("CellInfo", "SIM " + (simSlotIndex + 1) + " subId: " + subId + ", MCC: " + mcc + ", MNC: " + mnc);

                JSONObject simData = new JSONObject();
                simData.put("sim_slot", simSlotIndex + 1);
                simData.put("carrier", carrierName);
                simData.put("mcc", mcc);
                simData.put("mnc", mnc);

                TelephonyManager simTelephonyManager = telephonyManager.createForSubscriptionId(subId);
                if (simTelephonyManager == null) {
                    infoBuilder.append("  TelephonyManager for subId ").append(subId).append(" is null\n\n");
                    continue;
                }

                List<CellInfo> cellInfos = simTelephonyManager.getAllCellInfo();
                Log.d("CellInfo", "SIM " + (simSlotIndex + 1) + " cellInfos size: " + (cellInfos != null ? cellInfos.size() : 0));
                if (cellInfos == null || cellInfos.isEmpty()) {
                    infoBuilder.append("  No cell info available\n\n");
                    continue;
                }

                CellInfo primaryCell = null;
                int bestSignalStrength = Integer.MIN_VALUE;

                for (CellInfo cellInfo : cellInfos) {
                    if (!cellInfo.isRegistered()) {
                        continue;
                    }

                    String cellMcc = null;
                    String cellMnc = null;
                    int signalStrength = Integer.MIN_VALUE;

                    if (cellInfo instanceof CellInfoLte) {
                        CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                        CellIdentityLte identity = cellInfoLte.getCellIdentity();
                        cellMcc = identity.getMccString();
                        cellMnc = identity.getMncString();
                        signalStrength = cellInfoLte.getCellSignalStrength().getRsrp();
                    } else if (cellInfo instanceof CellInfoNr) {
                        CellInfoNr cellInfoNr = (CellInfoNr) cellInfo;
                        CellIdentityNr identity = (CellIdentityNr) cellInfoNr.getCellIdentity();
                        cellMcc = identity.getMccString();
                        cellMnc = identity.getMncString();
                        signalStrength = ((CellSignalStrengthNr) cellInfoNr.getCellSignalStrength()).getSsRsrp();
                    } else if (cellInfo instanceof CellInfoGsm) {
                        CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                        CellIdentityGsm identity = cellInfoGsm.getCellIdentity();
                        cellMcc = identity.getMccString();
                        cellMnc = identity.getMncString();
                        signalStrength = cellInfoGsm.getCellSignalStrength().getDbm();
                    } else if (cellInfo instanceof CellInfoWcdma) {
                        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cellInfo;
                        CellIdentityWcdma identity = cellInfoWcdma.getCellIdentity();
                        cellMcc = identity.getMccString();
                        cellMnc = identity.getMncString();
                        signalStrength = cellInfoWcdma.getCellSignalStrength().getDbm();
                    }

                    if (mcc != null && mnc != null && mcc.equals(cellMcc) && mnc.equals(cellMnc)) {
                        Log.d("CellInfo", "SIM " + (simSlotIndex + 1) + " found matching cell, Signal: " + signalStrength + ", Type: " + cellInfo.getClass().getSimpleName());
                        if (signalStrength > bestSignalStrength) {
                            bestSignalStrength = signalStrength;
                            primaryCell = cellInfo;
                        }
                    }
                }

                if (primaryCell != null) {
                    JSONObject cellData = new JSONObject();
                    if (primaryCell instanceof CellInfoLte) {
                        CellInfoLte cellInfoLte = (CellInfoLte) primaryCell;
                        CellIdentityLte identity = cellInfoLte.getCellIdentity();
                        CellSignalStrengthLte signal = cellInfoLte.getCellSignalStrength();

                        infoBuilder.append("  Network: LTE\n")
                                .append("  PCI: ").append(identity.getPci()).append("\n")
                                .append("  TAC: ").append(identity.getTac()).append("\n")
                                .append("  CI: ").append(identity.getCi()).append("\n")
                                .append("  Band: ").append(identity.getEarfcn()).append("\n")
                                .append("  RSRP: ").append(signal.getRsrp()).append(" dBm\n")
                                .append("  RSRQ: ").append(signal.getRsrq()).append(" dB\n\n");

                        cellData.put("network", "LTE");
                        cellData.put("pci", identity.getPci());
                        cellData.put("tac", identity.getTac());
                        cellData.put("ci", identity.getCi());
                        cellData.put("band", identity.getEarfcn());
                        cellData.put("rsrp", signal.getRsrp());
                        cellData.put("rsrq", signal.getRsrq());
                    } else if (primaryCell instanceof CellInfoNr) {
                        CellInfoNr cellInfoNr = (CellInfoNr) primaryCell;
                        CellIdentityNr identity = (CellIdentityNr) cellInfoNr.getCellIdentity();
                        CellSignalStrengthNr signal = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();

                        infoBuilder.append("  Network: 5G NR\n")
                                .append("  PCI: ").append(identity.getPci()).append("\n")
                                .append("  TAC: ").append(identity.getTac()).append("\n")
                                .append("  NCI: ").append(identity.getNci()).append("\n")
                                .append("  SS-RSRP: ").append(signal.getSsRsrp()).append(" dBm\n")
                                .append("  SS-RSRQ: ").append(signal.getSsRsrq()).append(" dB\n\n");

                        cellData.put("network", "5G NR");
                        cellData.put("pci", identity.getPci());
                        cellData.put("tac", identity.getTac());
                        cellData.put("nci", identity.getNci());
                        cellData.put("ss_rsrp", signal.getSsRsrp());
                        cellData.put("ss_rsrq", signal.getSsRsrq());
                    } else if (primaryCell instanceof CellInfoGsm) {
                        CellInfoGsm cellInfoGsm = (CellInfoGsm) primaryCell;
                        CellIdentityGsm identity = cellInfoGsm.getCellIdentity();
                        CellSignalStrengthGsm signal = cellInfoGsm.getCellSignalStrength();

                        infoBuilder.append("  Network: GSM\n")
                                .append("  LAC: ").append(identity.getLac()).append("\n")
                                .append("  CID: ").append(identity.getCid()).append("\n")
                                .append("  RSSI: ").append(signal.getDbm()).append(" dBm\n\n");

                        cellData.put("network", "GSM");
                        cellData.put("lac", identity.getLac());
                        cellData.put("cid", identity.getCid());
                        cellData.put("rssi", signal.getDbm());
                    } else if (primaryCell instanceof CellInfoWcdma) {
                        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) primaryCell;
                        CellIdentityWcdma identity = cellInfoWcdma.getCellIdentity();
                        CellSignalStrengthWcdma signal = cellInfoWcdma.getCellSignalStrength();

                        infoBuilder.append("  Network: WCDMA\n")
                                .append("  LAC: ").append(identity.getLac()).append("\n")
                                .append("  CID: ").append(identity.getCid()).append("\n")
                                .append("  RSSI: ").append(signal.getDbm()).append(" dBm\n\n");

                        cellData.put("network", "WCDMA");
                        cellData.put("lac", identity.getLac());
                        cellData.put("cid", identity.getCid());
                        cellData.put("rssi", signal.getDbm());
                    } else {
                        infoBuilder.append("  Unknown network type\n\n");
                    }
                    simData.put("cell", cellData);
                } else {
                    infoBuilder.append("  No registered cell found for this SIM\n\n");
                }
                sims.put("sim_" + (simSlotIndex + 1), simData);
            }
            jsonData.put("sims", sims);
        } catch (Exception e) {
            Log.e("HomeFragment", "JSON error: " + e.getMessage());
        }

        final String cellInfoString = infoBuilder.toString();
        Log.d("CellInfo", cellInfoString);
        requireActivity().runOnUiThread(() -> cellInfoText.setText(cellInfoString));

        if (webSocketService.getIsStreaming().getValue() != null && webSocketService.getIsStreaming().getValue()) {
            webSocketService.sendPrimaryCellData(jsonData.toString());
        }
    }

    @SuppressLint("MissingPermission")
    private int getSubscriptionId(int simSlotIndex) {
        SubscriptionManager sm = (SubscriptionManager) requireContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        List<SubscriptionInfo> subs = null;
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            subs = sm.getActiveSubscriptionInfoList();
        }

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