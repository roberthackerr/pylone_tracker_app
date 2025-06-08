package com.example.myapplication.ui.all;

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
import com.example.myapplication.databinding.FragmentAllBinding;
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
import android.telephony.TelephonyManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllFragment extends Fragment {

    private FragmentAllBinding binding;
    private TextView textView;
    private WebSocketService webSocketService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int INTERVAL_MS = 5000; // Poll every 5 seconds
    private final Runnable fetchCellInfoRunnable = new Runnable() {
        @Override
        public void run() {
            getNeighboringCellData();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    private static final Map<String, String> CARRIER_MAP = new HashMap<>();
    static {
        CARRIER_MAP.put("64601", "Airtel");
        CARRIER_MAP.put("64604", "TELMA");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAllBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        textView = binding.textAll;
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
            getNeighboringCellData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 101 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            getNeighboringCellData();
        } else {
            Toast.makeText(getContext(), "Permissions required for neighboring cell info", Toast.LENGTH_SHORT).show();
            textView.setText("Permissions required for neighboring cell info");
        }
    }

    @SuppressLint({"MissingPermission", "NewApi"})
    private void getNeighboringCellData() {
        TelephonyManager telephonyManager = (TelephonyManager) requireContext().getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null) {
            requireActivity().runOnUiThread(() -> textView.setText("TelephonyManager not available"));
            return;
        }

        List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
        Log.d("NeighboringCellInfo", "Total cells found: " + (cellInfos != null ? cellInfos.size() : 0));
        if (cellInfos == null || cellInfos.isEmpty()) {
            requireActivity().runOnUiThread(() -> textView.setText("No neighboring cell info available"));
            return;
        }

        StringBuilder infoBuilder = new StringBuilder();
        JSONObject jsonData = new JSONObject();
        JSONArray neighbors = new JSONArray();
        infoBuilder.append("Neighboring Cell Towers:\n\n");
        int neighborCount = 0;

        try {
            jsonData.put("type", "neighboring_cells");

            for (CellInfo cellInfo : cellInfos) {
                if (cellInfo.isRegistered()) {
                    continue;
                }

                neighborCount++;
                String carrierName = "Unknown Carrier";
                String mcc = null;
                String mnc = null;
                JSONObject cellData = new JSONObject();

                if (cellInfo instanceof CellInfoLte) {
                    CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                    CellIdentityLte identity = cellInfoLte.getCellIdentity();
                    CellSignalStrengthLte signal = cellInfoLte.getCellSignalStrength();

                    mcc = identity.getMccString();
                    mnc = identity.getMncString();
                    carrierName = getCarrierName(identity.getOperatorAlphaLong(), identity.getOperatorAlphaShort(), mcc, mnc);

                    infoBuilder.append("Neighbor ").append(neighborCount).append(" (LTE):\n")
                            .append("  Carrier: ").append(carrierName).append("\n")
                            .append("  PCI: ").append(identity.getPci()).append("\n")
                            .append("  TAC: ").append(identity.getTac()).append("\n")
                            .append("  CI: ").append(identity.getCi()).append("\n")
                            .append("  Band: ").append(identity.getEarfcn()).append("\n")
                            .append("  RSRP: ").append(signal.getRsrp()).append(" dBm\n")
                            .append("  RSRQ: ").append(signal.getRsrq()).append(" dB\n")
                            .append("  MCC: ").append(mcc != null ? mcc : "N/A").append("\n")
                            .append("  MNC: ").append(mnc != null ? mnc : "N/A").append("\n\n");

                    cellData.put("network", "LTE");
                    cellData.put("carrier", carrierName);
                    cellData.put("pci", identity.getPci());
                    cellData.put("tac", identity.getTac());
                    cellData.put("ci", identity.getCi());
                    cellData.put("band", identity.getEarfcn());
                    cellData.put("rsrp", signal.getRsrp());
                    cellData.put("rsrq", signal.getRsrq());
                    cellData.put("mcc", mcc != null ? mcc : "N/A");
                    cellData.put("mnc", mnc != null ? mnc : "N/A");
                } else if (cellInfo instanceof CellInfoNr) {
                    CellInfoNr cellInfoNr = (CellInfoNr) cellInfo;
                    CellIdentityNr identity = (CellIdentityNr) cellInfoNr.getCellIdentity();
                    CellSignalStrengthNr signal = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();

                    mcc = identity.getMccString();
                    mnc = identity.getMncString();
                    carrierName = getCarrierName(identity.getOperatorAlphaLong(), identity.getOperatorAlphaShort(), mcc, mnc);

                    infoBuilder.append("Neighbor ").append(neighborCount).append(" (5G NR):\n")
                            .append("  Carrier: ").append(carrierName).append("\n")
                            .append("  PCI: ").append(identity.getPci()).append("\n")
                            .append("  TAC: ").append(identity.getTac()).append("\n")
                            .append("  NCI: ").append(identity.getNci()).append("\n")
                            .append("  SS-RSRP: ").append(signal.getSsRsrp()).append(" dBm\n")
                            .append("  SS-RSRQ: ").append(signal.getSsRsrq()).append(" dB\n")
                            .append("  MCC: ").append(mcc != null ? mcc : "N/A").append("\n")
                            .append("  MNC: ").append(mnc != null ? mnc : "N/A").append("\n\n");

                    cellData.put("network", "5G NR");
                    cellData.put("carrier", carrierName);
                    cellData.put("pci", identity.getPci());
                    cellData.put("tac", identity.getTac());
                    cellData.put("nci", identity.getNci());
                    cellData.put("ss_rsrp", signal.getSsRsrp());
                    cellData.put("ss_rsrq", signal.getSsRsrq());
                    cellData.put("mcc", mcc != null ? mcc : "N/A");
                    cellData.put("mnc", mnc != null ? mnc : "N/A");
                } else if (cellInfo instanceof CellInfoGsm) {
                    CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                    CellIdentityGsm identity = cellInfoGsm.getCellIdentity();
                    CellSignalStrengthGsm signal = cellInfoGsm.getCellSignalStrength();

                    mcc = identity.getMccString();
                    mnc = identity.getMncString();
                    carrierName = getCarrierName(identity.getOperatorAlphaLong(), identity.getOperatorAlphaShort(), mcc, mnc);

                    infoBuilder.append("Neighbor ").append(neighborCount).append(" (GSM):\n")
                            .append("  Carrier: ").append(carrierName).append("\n")
                            .append("  LAC: ").append(identity.getLac()).append("\n")
                            .append("  CID: ").append(identity.getCid()).append("\n")
                            .append("  RSSI: ").append(signal.getDbm()).append(" dBm\n")
                            .append("  MCC: ").append(mcc != null ? mcc : "N/A").append("\n")
                            .append("  MNC: ").append(mnc != null ? mnc : "N/A").append("\n\n");

                    cellData.put("network", "GSM");
                    cellData.put("carrier", carrierName);
                    cellData.put("lac", identity.getLac());
                    cellData.put("cid", identity.getCid());
                    cellData.put("rssi", signal.getDbm());
                    cellData.put("mcc", mcc != null ? mcc : "N/A");
                    cellData.put("mnc", mnc != null ? mnc : "N/A");
                } else if (cellInfo instanceof CellInfoWcdma) {
                    CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cellInfo;
                    CellIdentityWcdma identity = cellInfoWcdma.getCellIdentity();
                    CellSignalStrengthWcdma signal = cellInfoWcdma.getCellSignalStrength();

                    mcc = identity.getMccString();
                    mnc = identity.getMncString();
                    carrierName = getCarrierName(identity.getOperatorAlphaLong(), identity.getOperatorAlphaShort(), mcc, mnc);

                    infoBuilder.append("Neighbor ").append(neighborCount).append(" (WCDMA):\n")
                            .append("  Carrier: ").append(carrierName).append("\n")
                            .append("  LAC: ").append(identity.getLac()).append("\n")
                            .append("  CID: ").append(identity.getCid()).append("\n")
                            .append("  RSSI: ").append(signal.getDbm()).append(" dBm\n")
                            .append("  MCC: ").append(mcc != null ? mcc : "N/A").append("\n")
                            .append("  MNC: ").append(mnc != null ? mnc : "N/A").append("\n\n");

                    cellData.put("network", "WCDMA");
                    cellData.put("carrier", carrierName);
                    cellData.put("lac", identity.getLac());
                    cellData.put("cid", identity.getCid());
                    cellData.put("rssi", signal.getDbm());
                    cellData.put("mcc", mcc != null ? mcc : "N/A");
                    cellData.put("mnc", mnc != null ? mnc : "N/A");
                } else {
                    infoBuilder.append("Neighbor ").append(neighborCount).append(" (Unknown):\n")
                            .append("  Carrier: ").append(carrierName).append("\n")
                            .append("  Unknown cell type\n\n");

                    cellData.put("network", "Unknown");
                    cellData.put("carrier", carrierName);
                }
                neighbors.put(cellData);
            }
            jsonData.put("neighbors", neighbors);
            jsonData.put("neighbor_count", neighborCount);
        } catch (Exception e) {
            Log.e("AllFragment", "JSON error: " + e.getMessage());
        }

        if (neighborCount == 0) {
            infoBuilder.append("No neighboring cell towers found\n");
        }

        final String cellInfoString = infoBuilder.toString();
        Log.d("NeighboringCellInfo", cellInfoString);
        requireActivity().runOnUiThread(() -> textView.setText(cellInfoString));

        if (webSocketService.getIsStreaming().getValue() != null && webSocketService.getIsStreaming().getValue()) {
            webSocketService.sendCellData(jsonData.toString());
        }
    }

    private String getCarrierName(CharSequence alphaLong, CharSequence alphaShort, String mcc, String mnc) {
        if (alphaLong != null && alphaLong.length() > 0) {
            return alphaLong.toString();
        }
        if (alphaShort != null && alphaShort.length() > 0) {
            return alphaShort.toString();
        }

        if (mcc != null && mnc != null) {
            String key = mcc + (mnc.length() == 2 ? "0" + mnc : mnc);
            String carrier = CARRIER_MAP.get(key);
            if (carrier != null) {
                return carrier;
            }
        }

        return "Unknown Carrier";
    }
}