package com.example.hellosdl2w.callservice;

import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;
import android.widget.Toast;

public class CallService extends InCallService {
    public static Call mycall;
    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        Toast.makeText(getApplicationContext(), "In call service ", Toast.LENGTH_SHORT).show();
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
