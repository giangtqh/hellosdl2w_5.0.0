package com.example.hellosdl2w.callservice;

import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

public class CallService extends InCallService {

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        new OngoingCall().setCall(call);
        Log.d("SHIT", "onCallAdded");
        CallActivity.start(this, call);
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        new OngoingCall().setCall(null);
    }
}