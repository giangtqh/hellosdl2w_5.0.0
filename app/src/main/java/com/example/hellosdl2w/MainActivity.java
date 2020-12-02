package com.example.hellosdl2w;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final String PORT = "com.example.PORT";
    public static final String ADDRESS = "com.example.ADDRESS";

    private static final int MY_PERMISSIONS_REQUEST_SEND_SMS = 1;
    private static final int MY_PERMISSIONS_REQUEST_RECEIVE_SMS = 2;
    private static final int MY_PERMISSIONS_REQUEST_READ_SMS = 3;
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACT = 4;
    public ArrayList listcontact;
    public ListView list;
    private static final int PERMISSION_ALL = 50;
    public static boolean isContactPermissionGranted = false;

    String[] PERMISSIONS = {
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.ANSWER_PHONE_CALLS,
            android.Manifest.permission.MANAGE_OWN_CALLS,
            android.Manifest.permission.READ_PHONE_NUMBERS
    };


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

        // The request code used in ActivityCompat.requestPermissions()
        // and returned in the Activity's onRequestPermissionsResult()

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

        // Check to see if SMS is enabled.

        checkForSmsPermission(MY_PERMISSIONS_REQUEST_RECEIVE_SMS);
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
                    instance.onSMSNotification(new SMSMessage("0967129109", "dummy sms message from mr.X", "", 0, 1));
                }
            }
        });

        final Button inCall = (Button) findViewById(R.id.btnInCall);
        inCall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "inCall button clicked");
                SdlService instance = SdlService.getInstance();
                if (instance != null) {
                    instance.onInCommingCall("0967129109", "Unknown");
                }
            }
        });

        final Button dial = (Button) findViewById(R.id.btnDial);
        dial.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "onDial button clicked");
                SdlService instance = SdlService.getInstance();
                if (instance != null) {
                    instance.onDial("0967129109", "Unknown ");
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

        final Button loadSMS = (Button) findViewById(R.id.btnLoadSms);
        loadSMS.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                checkForSmsPermission(MY_PERMISSIONS_REQUEST_READ_SMS);
                List<String> messages = new ArrayList<String>();
                Uri uriSMSURI = Uri.parse("content://sms/");
                Cursor cursor = getContentResolver().query(uriSMSURI, null, null, null, null);

                if (cursor.moveToFirst()) { // must check the result to prevent exception
                    do {
                        String body = cursor.getString(cursor.getColumnIndex("body"));
                        int read = cursor.getInt(cursor.getColumnIndex("read"));
                        String address = cursor.getString(cursor.getColumnIndex("address"));
                        String date_sent = cursor.getString(cursor.getColumnIndex("date_sent"));
//                        Log.d(TAG, address + " " + body + " " + read + " " + date_sent);
                        String full_content = "";
                        for (int idx = 0; idx < cursor.getColumnCount(); idx++) {
                            full_content += " | " + cursor.getColumnName(idx) + ":" + cursor.getString(idx);
                        }
                        Log.d(TAG, full_content);
                    } while (cursor.moveToNext());
                } else {
                    Toast.makeText(getApplicationContext(), "No SMS", Toast.LENGTH_LONG).show();
                }
            }
        });
        //
        checkForContactPermission();
        final Button contactList = (Button) findViewById(R.id.btnContact);
        contactList.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "clicked on button contact list");
                list = findViewById(R.id.list);
                listcontact = getAllContacts();
                ArrayAdapter adapter = new ArrayAdapter<String>(MainActivity.this,
                        android.R.layout.simple_list_item_1, android.R.id.text1, listcontact);
                list.setAdapter(adapter);
                }
        });
    }

    private ArrayList getAllContacts() {
        ArrayList<String> nameList = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

        if ((cur != null ? cur.getCount() : 0) > 0) {
            while (cur.moveToNext()) {
                String id = cur.getString(
                        cur.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cur.getString(cur.getColumnIndex(
                        ContactsContract.Contacts.DISPLAY_NAME));
                nameList.add(name);
                System.out.println("add name finished" + name + " + " + id);
                if (cur.getInt(cur.getColumnIndex( ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {

                    Cursor pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id}, null);
                    while (pCur.moveToNext()) {
                        String phoneNo = pCur.getString(pCur.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        System.out.println("in has phone number" + phoneNo);
                        nameList.add(phoneNo);
                    }
                    pCur.close();
                }
            }
        }

        if (cur != null) {
            cur.close();
        }

        return nameList;
    }

    /**
     * Checks whether the app has SMS permission.
     */
    private void checkForSmsPermission(int permission) {
        switch (permission) {
            case MY_PERMISSIONS_REQUEST_RECEIVE_SMS:
                if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECEIVE_SMS)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS},
                            MY_PERMISSIONS_REQUEST_RECEIVE_SMS);
                }
                break;
            case MY_PERMISSIONS_REQUEST_SEND_SMS:
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, getString(R.string.permission_not_granted));
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.SEND_SMS},
                            MY_PERMISSIONS_REQUEST_SEND_SMS);

                }
                break;
            case MY_PERMISSIONS_REQUEST_READ_SMS:
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, getString(R.string.permission_not_granted));
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_SMS},
                            MY_PERMISSIONS_REQUEST_READ_SMS);

                }
                break;
            default:
                Log.d(TAG, "request invalid permission.");
                break;
        }


    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }
    /**
     * Checks whether the app has Contact permission.
     */
    private void checkForContactPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, getString(R.string.permission_not_granted));
            // Permission has not been granted, therefore prompt the user to grant permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    MY_PERMISSIONS_REQUEST_READ_CONTACT);
        } else {
            isContactPermissionGranted = true;
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
        Log.d(TAG, "requestCode: "+requestCode);
        switch (requestCode) {
            case PERMISSION_ALL: {
                if (grantResults.length > 0) {
                    for (int i = 0; i < PERMISSIONS.length; i ++) {
                        Log.d(TAG, " Granted result for "+ PERMISSIONS[i] + " is: " + grantResults[i] );
                    }
                }
            }
            case MY_PERMISSIONS_REQUEST_READ_CONTACT: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted.
                    Log.d(TAG, "READ_CONTACT permission granted.");
                    isContactPermissionGranted = true;
                } else {
                    // Permission denied.
                    Log.d(TAG, "Failed to obtain READ_CONTACT permission.");
                    Toast.makeText(getApplicationContext(), "Fail to obtain READ_CONTACT permission.",
                            Toast.LENGTH_LONG).show();
                }
            }
            case MY_PERMISSIONS_REQUEST_SEND_SMS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted. Enable sms button.
                    Log.d(TAG, "SEND_SMS permission granted.");
                    enableSmsButton();
//                    checkForSmsPermission(MY_PERMISSIONS_REQUEST_RECEIVE_SMS);
                } else {
                    // Permission denied.
                    Log.d(TAG, "Failed to obtain SEND_SMS permission.");
                    Toast.makeText(getApplicationContext(), "Fail to obtain SEND_SMS permission.",
                            Toast.LENGTH_LONG).show();
                    // Disable the sms button.
                    disableSmsButton();
                }
            }
            break;
            case MY_PERMISSIONS_REQUEST_RECEIVE_SMS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "RECEIVE_SMS permission granted.");
                    checkForSmsPermission(MY_PERMISSIONS_REQUEST_READ_SMS);
                } else {
                    // Permission denied.
                    Log.d(TAG, "Failed to obtain RECEIVE_SMS permission.");
                    Toast.makeText(getApplicationContext(), "Failed to obtain RECEIVE_SMS permission.",
                            Toast.LENGTH_LONG).show();
                }
                break;
            case MY_PERMISSIONS_REQUEST_READ_SMS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "READ_SMS permission granted.");
                    checkForSmsPermission(MY_PERMISSIONS_REQUEST_SEND_SMS);
                } else {
                    // Permission denied.
                    Log.d(TAG, "Failed to obtain READ_SMS permission.");
                    Toast.makeText(getApplicationContext(), "Failed to obtain READ_SMS permission.",
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
        checkForSmsPermission(MY_PERMISSIONS_REQUEST_READ_SMS);
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
