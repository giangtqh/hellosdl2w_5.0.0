package com.example.hellosdl2w;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.widget.Toast;


import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

public class PhoneStateReceiver extends BroadcastReceiver {
    public static Context context;
    public TelephonyManager tm = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;
        tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        try {
            System.out.println("Receiver start");
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                String phoneNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                String name = getContactName(phoneNum, context);
                Toast.makeText(context, " On Call state: " + phoneNum + "-" + name, Toast.LENGTH_SHORT).show();
                SdlService instance = SdlService.getInstance();
                if (instance != null) {
                    instance.onInCommingCall(phoneNum);
                }
                rejectCall();

            }

            if ((state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK))) {
                String phoneNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                String name = getContactName(phoneNum,context);
                Toast.makeText(context, " On Dial state: " + phoneNum + "-" + name,Toast.LENGTH_SHORT).show();
                SdlService instance = SdlService.getInstance();
                if (instance != null) {
                    instance.onDial(phoneNum);
                }
            }

            if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                Toast.makeText(context, "Call end ", Toast.LENGTH_SHORT).show();
                SdlService instance = SdlService.getInstance();
                if (instance != null) {
                    instance.onEndCall();
                }
            }
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
