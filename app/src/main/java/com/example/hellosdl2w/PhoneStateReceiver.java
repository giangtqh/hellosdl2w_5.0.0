package com.example.hellosdl2w;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;


import com.example.hellosdl2w.callservice.CallService;
import com.example.hellosdl2w.callservice.OngoingCall;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

public class PhoneStateReceiver extends BroadcastReceiver {
    public static Context context;
    public TelephonyManager tm = null;
    private static boolean isRinginged = false;
    private static boolean isOffHooked = false;
    private static boolean isIdled = false;
    private static int x;
    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;
        tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        int my_statr = tm.getCallState();

        try {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            System.out.println("Receiver start " +my_statr + "  " + state);

            switch (state) {
                case "RINGING":
                    if(isRinginged) {
                        isRinginged = true;
                    }
                    else {

                        String phoneNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                        if(phoneNum != null) {
                            isRinginged = true;
                            String name = getContactName(phoneNum, context);
                            System.out.println("In comming call " + my_statr + "  " + state);
                            SdlService instance = SdlService.getInstance();
                            if(name.equals("")) {
                                name = "Unknown";
                            }
                            System.out.println("Name: -------------------: " + name);
                            if (instance != null) {
                                instance.onInCommingCall(phoneNum,name);
                            }
                        }
                    }
                    break;
                case "OFFHOOK":
                    if(isRinginged) {
                        isOffHooked = true;
                        Toast.makeText(context, " On accept the call", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        String phoneNumHook = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                        if(phoneNumHook != null) {
                            String nameHook = getContactName(phoneNumHook, context);
                            Toast.makeText(context, " On Dial state: " + phoneNumHook + "-" + nameHook, Toast.LENGTH_SHORT).show();
                            System.out.println("In dial " + my_statr + "  " + state);
                            if(nameHook.equals("")) {
                                nameHook = "Unknown";
                            }
                            System.out.println("Name: -------------------: " + nameHook);
                            SdlService instanceHook = SdlService.getInstance();
                            if (instanceHook != null) {
                                instanceHook.onDial(phoneNumHook, nameHook);
                            }
                            isOffHooked = true;
                        }
                    }
                    break;
                case "IDLE":
                    if(isRinginged || isOffHooked) {
                        x++;
                        Toast.makeText(context, "Call end: " +x, Toast.LENGTH_SHORT).show();
                        System.out.println("In idle " + my_statr + "  " + state);
                        SdlService instanceIdle = SdlService.getInstance();
                        if (instanceIdle != null) {
                            instanceIdle.onEndCall();
                        }
                        isRinginged = false;
                        isOffHooked = false;
                    }
                    break;
            }

//            if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
//                last_state = state;
//                isEnded = false;
//                String phoneNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
//                String name = getContactName(phoneNum, context);
//                //Toast.makeText(context, " On Call state: " + phoneNum + "-" + name, Toast.LENGTH_SHORT).show();
//                SdlService instance = SdlService.getInstance();
//                if (instance != null) {
//                    instance.onInCommingCall(phoneNum);
//                }
//            }
//
//            if ((state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK))) {
//                if (last_state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
//
//                }
//                else {
//                    isEnded = false;
//                    String phoneNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
//                    String name = getContactName(phoneNum, context);
//                    Toast.makeText(context, " On Dial state: " + phoneNum + "-" + name, Toast.LENGTH_SHORT).show();
//                    System.out.println("In dial " + my_statr + "  " + state);
//                    SdlService instance = SdlService.getInstance();
//                    if (instance != null) {
//                        instance.onDial(phoneNum);
//                    }
//                }
//            }
//
//            if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
//                last_state = "";
//                if (!isEnded) {
//                    x++;
//                    Log.d("End call", "end call" + x);
//                    Toast.makeText(context, "Call end: " + x, Toast.LENGTH_SHORT).show();
//
//                    SdlService instance = SdlService.getInstance();
//                    if (instance != null) {
//                        instance.onEndCall();
//                    }
//                }
//                isEnded = true;
//            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getContactName(String phoneNumber, Context context)
    {
        Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(phoneNumber));

        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

        String contactName = "";
        Cursor cursor=context.getContentResolver().query(uri,projection,null,null,null);

        if (cursor != null) {
            if(cursor.moveToFirst()) {
                contactName = cursor.getString(0);
            }
            cursor.close();
        }

        return contactName;
    }

    private void rejectCall(){
        try {
            // Get the getITelephony() method
            Class<?> classTelephony = Class.forName(tm.getClass().getName());
            Method method = classTelephony.getDeclaredMethod("getITelephony");
            // Disable access check
            method.setAccessible(true);
            // Invoke getITelephony() to get the ITelephony interface
            Object telephonyInterface = method.invoke(tm);
                // Get the endCall method from ITelephony
            Class<?> telephonyInterfaceClass = Class.forName(telephonyInterface.getClass().getName());
            Method methodEndCall = telephonyInterfaceClass.getDeclaredMethod("endCall");
            // Invoke endCall()
            methodEndCall.invoke(telephonyInterface);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void acceptCall() {

    }
}
