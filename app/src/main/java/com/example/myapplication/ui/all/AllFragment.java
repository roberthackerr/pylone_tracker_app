package com.example.myapplication.ui.all;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.databinding.FragmentAllBinding;
import com.example.myapplication.databinding.FragmentDashboardBinding;
import com.example.myapplication.ui.dashboard.DashboardViewModel;

public class AllFragment extends Fragment {

    private FragmentAllBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        AllViewModel dashboardViewModel =
                new ViewModelProvider(this).get(AllViewModel.class);

        binding = FragmentAllBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textAll;
        dashboardViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
