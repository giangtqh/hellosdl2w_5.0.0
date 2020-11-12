package com.example.hellosdl2w;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final String PORT = "com.example.PORT";
    public static final String ADDRESS = "com.example.ADDRESS";

    private static final int MY_PERMISSIONS_REQUEST_SEND_SMS = 1;
    private static final int MY_PERMISSIONS_REQUEST_RECEIVE_SMS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //If we are connected to a module we want to start our SdlService
        if (BuildConfig.TRANSPORT.equals("MULTI") || BuildConfig.TRANSPORT.equals("MULTI_HB")) {
            SdlReceiver.queryForConnectedService(this);
        } else if (BuildConfig.TRANSPORT.equals("TCP")) {
            final EditText eAddress = (EditText) findViewById(R.id.editIP);
            final EditText ePort = (EditText) findViewById(R.id.editPort);

            final Intent proxyIntent = new Intent((Context) this, SdlService.class);
            final Button button = (Button) findViewById(R.id.btConnect);
            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // Code here executes on main thread after user presses button
                    Log.d(TAG, "Address: " + eAddress.getText());
                    Log.d(TAG, "Port: " + ePort.getText());
                    proxyIntent.putExtra(ADDRESS, eAddress.getText().toString());
                    proxyIntent.putExtra(PORT, Integer.parseInt(ePort.getText().toString()));
                    startService(proxyIntent);
                }
            });
//            startService(proxyIntent);
        }

        // Check to see if SMS is enabled.
        checkForSmsPermission();
        enableSmsButton();
        final ImageButton btnSendSms = (ImageButton) findViewById(R.id.message_icon);
        btnSendSms.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes on main thread after user presses button
                smsSendMessage(v);
            }
        });

        // For Test only
        final Button pushSMS = (Button) findViewById(R.id.btnPushDummySMS);
        pushSMS.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "push SMS button clicked");
                SdlService instance = SdlService.getInstance();
                if (instance != null) {
                    instance.onSMSNotification(new CustomAlert("ON_SMS", "0967129109","push dummy sms message"));
                }
            }
        });

        final Button inCall = (Button) findViewById(R.id.btnInCall);
        inCall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "inCall button clicked");
                SdlService instance = SdlService.getInstance();
                if (instance != null) {
                    instance.onInCommingCall("0967129109");
                }
            }
        });

        final Button dial = (Button) findViewById(R.id.btnDial);
        dial.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "onDial button clicked");
                SdlService instance = SdlService.getInstance();
                if (instance != null) {
                    instance.onDial("0967129109");
                } else {
                    Log.d(TAG, "SdlService is not start.");
                }
            }
        });

        final Button endCall = (Button) findViewById(R.id.btnEndCall);
        endCall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "clicked on button endCall");
                SdlService instance = SdlService.getInstance();
                if (instance != null) {
                    instance.onEndCall();
                }
            }
        });
    }

    /**
     * Checks whether the app has SMS permission.
     */
    private void checkForSmsPermission() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS},
                    MY_PERMISSIONS_REQUEST_RECEIVE_SMS);
        }
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, getString(R.string.permission_not_granted));
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    MY_PERMISSIONS_REQUEST_SEND_SMS);

        }
    }

    /**
     * Processes permission request codes.
     *
     * @param requestCode  The request code passed in requestPermissions()
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        Log.d(TAG, "request code " + requestCode);
        for (String i : permissions) {
            Log.d(TAG, "permissions: " + i);
        }
        Log.d(TAG, "permission size: " + permissions.length + "grant size: " + grantResults.length);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_SEND_SMS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted. Enable sms button.
                    enableSmsButton();
                } else {
                    // Permission denied.
                    Log.d(TAG, getString(R.string.failure_permission));
                    Toast.makeText((Context) this, getString(R.string.failure_permission),
                            Toast.LENGTH_LONG).show();
                    // Disable the sms button.
                    disableSmsButton();
                }
            }
            break;
            case MY_PERMISSIONS_REQUEST_RECEIVE_SMS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    // Permission denied.
                    Log.d(TAG, getString(R.string.failure_permission));
                    Toast.makeText((Context) this, getString(R.string.failure_permission),
                            Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    /**
     * Defines a string (destinationAddress) for the phone number
     * and gets the input text for the SMS message.
     * Uses SmsManager.sendTextMessage to send the message.
     * Before sending, checks to see if permission is granted.
     *
     * @param view View (message_icon) that was clicked.
     */
    public void smsSendMessage(View view) {
        EditText editText = (EditText) findViewById(R.id.editText_main);
        // Set the destination phone number to the string in editText.
        String destinationAddress = editText.getText().toString();
        if (destinationAddress.matches("")) {
            Toast.makeText((Context) this, "Please enter phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        // Find the sms_message view.
        EditText smsEditText = (EditText) findViewById(R.id.sms_message);
        // Get the text of the sms message.
        String smsMessage = smsEditText.getText().toString();
        if (smsMessage.matches("")) {
            Toast.makeText((Context) this, "Please enter message body", Toast.LENGTH_SHORT).show();
            return;
        }
        // Set the service center address if needed, otherwise null.
        String scAddress = null;
        // Set pending intents to broadcast
        // when message sent and when delivered, or set to null.
        PendingIntent sentIntent = null, deliveryIntent = null;
        // Check for permission first.
        checkForSmsPermission();
        // Use SmsManager.
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(destinationAddress, scAddress, smsMessage,
                sentIntent, deliveryIntent);
    }

    /**
     * Makes the sms button (message icon) invisible so that it can't be used,
     * and makes the Retry button visible.
     */
    private void disableSmsButton() {
        Toast.makeText((Context) this, R.string.sms_disabled, Toast.LENGTH_LONG).show();
        ImageButton smsButton = (ImageButton) findViewById(R.id.message_icon);
        smsButton.setVisibility(View.INVISIBLE);
        Button retryButton = (Button) findViewById(R.id.button_retry);
        retryButton.setVisibility(View.VISIBLE);
    }

    /**
     * Makes the sms button (message icon) visible so that it can be used.
     */
    private void enableSmsButton() {
        ImageButton smsButton = (ImageButton) findViewById(R.id.message_icon);
        smsButton.setVisibility(View.VISIBLE);
    }

    /**
     * Sends an intent to start the activity.
     *
     * @param view View (Retry button) that was clicked.
     */
    public void retryApp(View view) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
