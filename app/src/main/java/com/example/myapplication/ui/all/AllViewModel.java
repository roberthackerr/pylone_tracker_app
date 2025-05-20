package com.example.myapplication.ui.all;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class AllViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public AllViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("All Fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
