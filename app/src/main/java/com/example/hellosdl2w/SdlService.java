package com.example.hellosdl2w;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.icu.text.UnicodeSetSpanner;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.CallLog;
import android.util.Log;
import android.widget.Toast;
import android.content.ContentResolver;
import android.provider.ContactsContract;

import com.example.hellosdl2w.callservice.CallActivity;
import com.example.hellosdl2w.callservice.OngoingCall;
import com.google.gson.Gson;
import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.screen.OnButtonListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.lifecycle.LifecycleConfigurationUpdate;
import com.smartdevicelink.managers.screen.SoftButtonObject;
import com.smartdevicelink.managers.screen.SoftButtonState;
import com.smartdevicelink.managers.screen.choiceset.ChoiceCell;
import com.smartdevicelink.managers.screen.choiceset.ChoiceSet;
import com.smartdevicelink.managers.screen.choiceset.ChoiceSetSelectionListener;
import com.smartdevicelink.managers.screen.menu.MenuCell;
import com.smartdevicelink.managers.screen.menu.MenuSelectionListener;
import com.smartdevicelink.managers.screen.menu.VoiceCommand;
import com.smartdevicelink.managers.screen.menu.VoiceCommandSelectionListener;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.rpc.AddCommand;
import com.smartdevicelink.proxy.rpc.Alert;
import com.smartdevicelink.proxy.rpc.DeleteCommand;
import com.smartdevicelink.proxy.rpc.MenuParams;
import com.smartdevicelink.proxy.rpc.OnButtonEvent;
import com.smartdevicelink.proxy.rpc.OnButtonPress;
import com.smartdevicelink.proxy.rpc.OnCommand;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.SoftButton;
import com.smartdevicelink.proxy.rpc.Speak;
import com.smartdevicelink.proxy.rpc.TTSChunk;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.ButtonName;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.InteractionMode;
import com.smartdevicelink.proxy.rpc.enums.Language;
import com.smartdevicelink.proxy.rpc.enums.MenuLayout;
import com.smartdevicelink.proxy.rpc.enums.PredefinedWindows;
import com.smartdevicelink.proxy.rpc.enums.SoftButtonType;
import com.smartdevicelink.proxy.rpc.enums.SpeechCapabilities;
import com.smartdevicelink.proxy.rpc.enums.SystemAction;
import com.smartdevicelink.proxy.rpc.enums.TextAlignment;
import com.smartdevicelink.proxy.rpc.enums.TriggerSource;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TCPTransportConfig;
import com.smartdevicelink.util.DebugTool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class SdlService extends Service {

    private static final String TAG = "SDL Service";

    private static final String APP_NAME = "Amazing SDL2W App";
    private static final String APP_NAME_ES = "Hola Sdl";
    private static final String APP_NAME_FR = "Bonjour Sdl";
    private static final String APP_ID = "8678309";

    private static final String ICON_FILENAME = "hello_sdl_icon.png";
    private static final int FOREGROUND_SERVICE_ID = 111;

    // TCP/IP transport config
    // The default port is 12345
    // The IP is of the machine that is running SDL Core
    private static final int TCP_PORT = 12345;
    private static final String DEV_MACHINE_IP_ADDRESS = "m.sdl.tools";

    // variable to create and call functions of the SyncProxy
    private SdlManager sdlManager = null;

    private static final int PHONE_BTN_ID = 100;
    private static final int CONTACT_BTN_ID = 101;
    private static final int SMS_BTN_ID = 102;
    private static final int ACCEPT_BTN_ID = 103;
    private static final int DENY_BTN_ID = 104;

    // Contact commands ID range from 500->999, Phone: 1000->1999, SMS: 2000->2999
    private static int mContactCommandId = 500;
    private static int mPhoneCommandId = 1000;
    private static int mSMSCommandId = 2000;

    // declare softbutton for Phone, Contact and SMS
    private SoftButtonObject mPhoneSoftBtn = null;
    private SoftButtonObject mContactSoftBtn = null;
    private SoftButtonObject mSMSSoftBtn = null;
    private SoftButton mAcceptSoftBtn = null;
    private SoftButton mDenySoftBtn = null;

    private InfoType mActiveInfoType = InfoType.NONE; // Stores the current type of information being displayed
    private static SdlService instance = null;

    private List<SMSMessage> mSMSMessages = null;
    private Iterator<SMSMessage> mSMSIterator = null;
    private Map<Integer, SMSMessage> mMapSMSCmdId = new HashMap<>();

    private List<ContactItem> mContactList = null;
    private Iterator<ContactItem> mContactIterator = null;
    private Map<Integer, ContactItem> mMapContactCmdId = new HashMap<>();

    private List<CallLogItem> mCallLogMessage = null;
    private Iterator<CallLogItem> mCallLogIterator = null;
    private Map<Integer, CallLogItem> mMapCallLogCmdId = new HashMap<>();

    private Iterator<Integer> mMapCmdIterator = null;
    private Iterator<Integer> mMapContactCmdIterator = null;
    // variable used to increment correlation ID for every request sent to SDL
    private int mCmdPosIncIndex = 0;
    private Gson mGson = new Gson();

    static {
        instance = null;
    }

    public static synchronized SdlService getInstance() {
        return instance;
    }

    private static synchronized void setInstance(SdlService service) {
        instance = service;
    }

    public SdlService() {
        SdlService.setInstance(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        SdlService.setInstance(this);

        //softbutton for Phone
        SoftButtonState mShowPhoneState = new SoftButtonState("mShowPhoneState", getResources().getString(R.string.phone), null);
        mPhoneSoftBtn = new SoftButtonObject("mPhoneSoftBtn", Collections.singletonList(mShowPhoneState), mShowPhoneState.getName(), new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                mActiveInfoType = InfoType.PHONE;
                Toast.makeText(getApplicationContext(), "Phone clicked", Toast.LENGTH_LONG).show();
                createCallLogList();
                updateHmi();
            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        });
        mPhoneSoftBtn.setButtonId(PHONE_BTN_ID);

        //softbutton for Contact
        SoftButtonState mShowContactState = new SoftButtonState("mShowContactState", getResources().getString(R.string.contact), null);
        mContactSoftBtn = new SoftButtonObject("mContactSoftBtn", Collections.singletonList(mShowContactState), mShowContactState.getName(), new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                mActiveInfoType = InfoType.CONTACT;
                Toast.makeText(getApplicationContext(), "Contact clicked", Toast.LENGTH_LONG).show();
                createContactList();
                sendContactList();
            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        });
        mContactSoftBtn.setButtonId(CONTACT_BTN_ID);

        //softbutton for SMS
        SoftButtonState mShowSMSState = new SoftButtonState("mShowSMSState", getResources().getString(R.string.sms), null);
        mSMSSoftBtn = new SoftButtonObject("mSMSSoftBtn", Collections.singletonList(mShowSMSState), mShowSMSState.getName(), new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                mActiveInfoType = InfoType.SMS;
                Toast.makeText(getApplicationContext(), "SMS clicked", Toast.LENGTH_LONG).show();
                createSMSList();
                updateHmi();
            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        });
        mSMSSoftBtn.setButtonId(SMS_BTN_ID);

        //Define softbutton for Accept and Deny Call
        mDenySoftBtn = new SoftButton();
        mDenySoftBtn.setSoftButtonID(DENY_BTN_ID);
        mDenySoftBtn.setText(getResources().getString(R.string.deny));
        mDenySoftBtn.setType(SoftButtonType.SBT_TEXT);
        mDenySoftBtn.setIsHighlighted(false);
        mDenySoftBtn.setSystemAction(SystemAction.DEFAULT_ACTION);

        mAcceptSoftBtn = new SoftButton();
        mAcceptSoftBtn.setSoftButtonID(ACCEPT_BTN_ID);
        mAcceptSoftBtn.setText(getResources().getString(R.string.accept));
        mAcceptSoftBtn.setType(SoftButtonType.SBT_TEXT);
        mAcceptSoftBtn.setIsHighlighted(false);
        mAcceptSoftBtn.setSystemAction(SystemAction.DEFAULT_ACTION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterForeground();
        }
    }

    // Helper method to let the service enter foreground mode
    @SuppressLint("NewApi")
    public void enterForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Notification serviceNotification = new Notification.Builder(this, channel.getId())
                        .setContentTitle("Connected through SDL")
                        .setSmallIcon(R.drawable.ic_sdl)
                        .build();
                startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startProxy(intent);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        if (sdlManager != null) {
            sdlManager.dispose();
        }

        super.onDestroy();
    }

    private void startProxy(Intent intent) {
        // This logic is to select the correct transport and security levels defined in the selected build flavor
        // Build flavors are selected by the "build variants" tab typically located in the bottom left of Android Studio
        // Typically in your app, you will only set one of these.
        if (sdlManager == null) {
            Log.i(TAG, "Starting SDL Proxy");
            // Enable DebugTool for debug build type
            if (BuildConfig.DEBUG) {
                DebugTool.enableDebugTool();
            }
            BaseTransportConfig transport = null;
            if (BuildConfig.TRANSPORT.equals("MULTI")) {
                int securityLevel;
                if (BuildConfig.SECURITY.equals("HIGH")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH;
                } else if (BuildConfig.SECURITY.equals("MED")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED;
                } else if (BuildConfig.SECURITY.equals("LOW")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW;
                } else {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
                }
                transport = new MultiplexTransportConfig(this, APP_ID, securityLevel);
            } else if (BuildConfig.TRANSPORT.equals("TCP")) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    String addr = bundle.getString(MainActivity.ADDRESS);
                    int port = bundle.getInt(MainActivity.PORT);
                    Log.v(TAG, "SHIT Address: " + addr + ":" + port);
                    transport = new TCPTransportConfig(port, addr, true);
                } else {
                    transport = new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true);
                }
            } else if (BuildConfig.TRANSPORT.equals("MULTI_HB")) {
                MultiplexTransportConfig mtc = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
                mtc.setRequiresHighBandwidth(true);
                transport = mtc;
            }

            // The app type to be used
            Vector<AppHMIType> appType = new Vector<>();
            appType.add(AppHMIType.DEFAULT);

            // The manager listener helps you know when certain events that pertain to the SDL Manager happen
            // Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
            SdlManagerListener listener = new SdlManagerListener() {
                @Override
                public void onStart() {
                    // HMI Status Listener
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnHMIStatus onHMIStatus = (OnHMIStatus) notification;
                            if (onHMIStatus.getWindowID() != null && onHMIStatus.getWindowID() != PredefinedWindows.DEFAULT_WINDOW.getValue()) {
                                return;
                            }
                            if (onHMIStatus.getHmiLevel() == HMILevel.HMI_FULL && onHMIStatus.getFirstRun()) {
                                //setVoiceCommands();
//                                sendMenus();
                                //sendCommands();
                                //performWelcomeSpeak();
                                //performWelcomeShow();
                                //preloadChoices();
                                //subscribeToButtons();
                                showFeatures();
                                //sendContactlist();
                                //startAudioStream();
                            }
                        }
                    });

                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_COMMAND, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnCommand onCommand = (OnCommand) notification;
                            int cmdId = onCommand.getCmdID();
                            //Toast.makeText(getApplicationContext(), "onCommand Id: " + cmdId, Toast.LENGTH_LONG).show();
                            if (cmdId < 1000) {
                                String phone_num = mMapContactCmdId.get(cmdId).number;
                                Toast.makeText(getApplicationContext(), "Makecall from contact " + phone_num, Toast.LENGTH_LONG).show();
                                CallActivity.makeCall(getApplicationContext(), phone_num);
                            } else if (cmdId < 2000) {
                                String phone_num = mMapCallLogCmdId.get(cmdId).number;
                                Toast.makeText(getApplicationContext(), "Makecall from CallLog " + phone_num, Toast.LENGTH_LONG).show();
                                CallActivity.makeCall(getApplicationContext(), phone_num);
                            } else if (cmdId < 3000) {
                                // sms message commands
                            } else {
                                Toast.makeText(getApplicationContext(), "Invalid command Id: " + cmdId, Toast.LENGTH_LONG).show();
                            }
                        }
                    });

                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_TOUCH_EVENT, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            Toast.makeText(getApplicationContext(), "On touch event", Toast.LENGTH_LONG).show();
                        }
                    });

                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_BUTTON_PRESS, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnButtonPress buttonPress = (OnButtonPress) notification;
                            int btnId = buttonPress.getCustomButtonID();
                            switch (btnId) {
                                    case DENY_BTN_ID:
                                            Toast.makeText(getApplicationContext(), "Oh shit, Deny button clicked", Toast.LENGTH_LONG).show();
                                            OngoingCall.hangup();
                                            break;
                                    case ACCEPT_BTN_ID:
                                            Toast.makeText(getApplicationContext(), "Oh man, Accept button has clicked", Toast.LENGTH_LONG).show();
                                            OngoingCall.answer();
                                            break;
                                }
                            }
                    });

                }

                @Override
                public void onDestroy() {
                    SdlService.this.stopSelf();
                }

                @Override
                public void onError(String info, Exception e) {
                }

                @Override
                public LifecycleConfigurationUpdate managerShouldUpdateLifecycle(Language language, Language hmiLanguage) {
                    boolean isNeedUpdate = false;
                    String appName = APP_NAME;
                    String ttsName = APP_NAME;
                    switch (language) {
                        case ES_MX:
                            isNeedUpdate = true;
                            ttsName = APP_NAME_ES;
                            break;
                        case FR_CA:
                            isNeedUpdate = true;
                            ttsName = APP_NAME_FR;
                            break;
                        default:
                            break;
                    }
                    switch (hmiLanguage) {
                        case ES_MX:
                            isNeedUpdate = true;
                            appName = APP_NAME_ES;
                            break;
                        case FR_CA:
                            isNeedUpdate = true;
                            appName = APP_NAME_FR;
                            break;
                        default:
                            break;
                    }
                    if (isNeedUpdate) {
                        Vector<TTSChunk> chunks = new Vector<>(Collections.singletonList(new TTSChunk(ttsName, SpeechCapabilities.TEXT)));
                        return new LifecycleConfigurationUpdate(appName, null, chunks, null);
                    } else {
                        return null;
                    }
                }
            };

            // Create App Icon, this is set in the SdlManager builder
            SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true);

            // The manager builder sets options for your session
            SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
            builder.setAppTypes(appType);
            builder.setTransportType(transport);
            builder.setAppIcon(appIcon);
            sdlManager = builder.build();
            sdlManager.start();
        }
    }

    /**
     * Shows figure
     */
    private void showFeatures() {
        // 4.7 change SoftButton to SoftButtonObject
        Vector<SoftButtonObject> softButtons = new Vector<SoftButtonObject>();
        softButtons.add(mPhoneSoftBtn);
        softButtons.add(mContactSoftBtn);
        softButtons.add(mSMSSoftBtn);

        // 4.7
        sdlManager.getScreenManager().beginTransaction();
        sdlManager.getScreenManager().setTextField1(getResources().getString(R.string.app_main_field1));
        sdlManager.getScreenManager().setTextField2(getResources().getString(R.string.app_main_field2));
        sdlManager.getScreenManager().setTextField3(getResources().getString(R.string.app_main_field3));
        sdlManager.getScreenManager().setTextField4(getResources().getString(R.string.app_main_field4));
        sdlManager.getScreenManager().setTextAlignment(TextAlignment.LEFT_ALIGNED);
        //sdlManager.getScreenManager().setPrimaryGraphic(new SdlArtwork(mConditionIconFileName, FileType.GRAPHIC_PNG, conditionsID, true));
        sdlManager.getScreenManager().setSoftButtonObjects(softButtons);

        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                Log.i(TAG, "ScreenManager update complete: " + success);

            }
        });
    }

    private void updateHmi() {
        switch (mActiveInfoType) {
            case PHONE:
                showCallLogList();
                break;
            case CONTACT:

                break;
            case SMS:
                showSMSList();
                break;
            default:
                break;
        }
    }

    // Alert has 3 mainField, use mainField1 for messageType, mainField2 for message body
    // Alert message type:
    // ON_CALL, ON_END_CALL, ON_DIAL, ON_SMS
    public void onSMSNotification(SMSMessage message) {
        Alert alertRequest = new Alert();
        alertRequest.setAlertText1("ON_SMS");
        alertRequest.setAlertText2(message.address);
        alertRequest.setAlertText3(message.body);
        sdlManager.sendRPC(alertRequest);
        //Toast.makeText(this, "SdlService::onSMSNotification(): " + alert.message, Toast.LENGTH_LONG).show();
    }

    public void onInCommingCall(String number, String name) {
        Alert alert = new Alert();
        alert.setAlertText1("ON_CALL");
        alert.setAlertText2(number);
        alert.setAlertText3(name);
        List<SoftButton> softBtns1 = new ArrayList<>();
        softBtns1.add(mAcceptSoftBtn);
        softBtns1.add(mDenySoftBtn);
        alert.setSoftButtons(softBtns1);
        sdlManager.sendRPC(alert);
        Toast.makeText(this, "Incomming call", Toast.LENGTH_LONG).show();
    }

    public void onDial(String number, String name) {
        Alert alert = new Alert();
        alert.setAlertText1("ON_DIAL");
        alert.setAlertText2(number);
        alert.setAlertText3(name);
        List<SoftButton> softBtns = new ArrayList<>();
        softBtns.add(mDenySoftBtn);
        alert.setSoftButtons(softBtns);
        sdlManager.sendRPC(alert);
        Toast.makeText(this, "Dialing " + number, Toast.LENGTH_LONG).show();
    }

    public void onEndCall() {
        Alert alert = new Alert();
        alert.setAlertText1("ON_END_CALL");
        sdlManager.sendRPC(alert);
        Toast.makeText(this, "End call.", Toast.LENGTH_LONG).show();
    }

    public void onStartCall() {
        Alert alert = new Alert();
        alert.setAlertText1("ON_START_CALL");
        sdlManager.sendRPC(alert);
        Toast.makeText(this, "Start call.", Toast.LENGTH_LONG).show();
    }

    /**
     * Display the SMS log.
     */
    private void createSMSList() {
        mSMSMessages = new ArrayList<SMSMessage>();
        // get all sms messages includes inbox and sent
        Uri uriSMSURI = Uri.parse("content://sms/");
        Cursor cursor = getContentResolver().query(uriSMSURI, null, null, null, null);

        if ((cursor != null) && cursor.moveToFirst()) {
            do {
                String body = cursor.getString(cursor.getColumnIndex("body"));
                String address = cursor.getString(cursor.getColumnIndex("address"));
                String name = getNameByPhoneNumber(address);
                if(!name.equals("")){
                    address = name;
                }
                String date = cursor.getString(cursor.getColumnIndex("date"));
                int read = cursor.getInt(cursor.getColumnIndex("read"));
                int type = cursor.getInt(cursor.getColumnIndex("type"));
                if (body.length() > 400) {
                    body.substring(0,400);
                }
                mSMSMessages.add(new SMSMessage(address, body, date, read, type));
            } while (cursor.moveToNext());
        } else {
            Toast.makeText(getApplicationContext(), "No SMS", Toast.LENGTH_LONG).show();
        }

        if (cursor != null) {
            cursor.close();
        }
    }

    private void deleteCommands() {
        if (mMapCmdIterator.hasNext()) {
            final int cmdId = mMapCmdIterator.next();
            DeleteCommand command = new DeleteCommand(cmdId);
            command.setOnRPCResponseListener(new OnRPCResponseListener() {
                @Override
                public void onResponse(int correlationId, RPCResponse response) {
                    mMapCmdIterator.remove();
                    deleteCommands();
                }
            });
            sdlManager.sendRPC(command);
        } else {
            mSMSIterator = mSMSMessages.iterator();
            addSMSCommands();
        }
    }

    private void addSMSCommands() {
        if (mSMSIterator.hasNext()) {
            final SMSMessage item = mSMSIterator.next();
            final int cmdId = generateSMSCmdId();
            AddCommand command = new AddCommand(cmdId);
            command.setOnRPCResponseListener(new OnRPCResponseListener() {
                @Override
                public void onResponse(int correlationId, RPCResponse response) {
                    mMapSMSCmdId.put(cmdId, item); // put succeed command id only
                    addSMSCommands();
                }
            });
            String json = mGson.toJson(item);
            MenuParams params = new MenuParams();
            params.setMenuName(json);
//            params.setPosition(mCmdPosIncIndex++);
//            params.setPosition(0);
            command.setMenuParams(params);
            sdlManager.sendRPC(command);
        } else {
            mCmdPosIncIndex = 0;
            // reset iterator
            mSMSIterator = mSMSMessages.iterator();
            // Send an alert to notify list items finished
            Alert alert = new Alert();
            alert.setAlertText1("SMS_FILLED");
            sdlManager.sendRPC(alert);
        }
    }

    public void showSMSList() {
        if (mMapSMSCmdId.size() > 0) {
            mMapCmdIterator = mMapSMSCmdId.keySet().iterator();
            deleteCommands();
        } else {
            if (mSMSMessages.size() > 0) {
                mSMSIterator = mSMSMessages.iterator();
                addSMSCommands();
            }
            else {
                Alert alert = new Alert();
                alert.setAlertText1("SMS_FILLED");
                sdlManager.sendRPC(alert);
            }
        }
    }

    /**
     * Display the contact list.
     */
    private void createContactList() {
        mContactList = new ArrayList<ContactItem>();
        ContentResolver cr = getContentResolver();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null,  null);

        if ((cur != null ? cur.getCount() : 0) > 0) {
            while (cur.moveToNext()) {
                String id = cur.getString(
                        cur.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cur.getString(cur.getColumnIndex(
                        ContactsContract.Contacts.DISPLAY_NAME));
                String phoneNo = "";
                if (cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                    Cursor pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id}, null);
                    if (pCur.moveToNext()) {
                        phoneNo = pCur.getString(pCur.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                    }
                    pCur.close();
                }
                mContactList.add ( new ContactItem(name, phoneNo));
            }
        }
        else {
            // no data in contact list
            Toast.makeText(getApplicationContext(), "No Contact list to show", Toast.LENGTH_LONG).show();
        }
        if (cur != null)
            cur.close();
    }

    public void sendContactList() {
        if ((mMapContactCmdId != null) && (mMapContactCmdId.size() > 0)) {
            mMapContactCmdIterator = mMapContactCmdId.keySet().iterator();
            deleteContactCommands();
        } else {
            if ((mContactList != null) && (mContactList.size() > 0)) {
                mContactIterator = mContactList.iterator();
                addContactCommands();
            }
            else {
                Alert alert = new Alert();
                alert.setAlertText1("CONTACT_FILLED");
                sdlManager.sendRPC(alert);
            }
        }
    }

    private void addContactCommands() {
        if ((mContactIterator != null) && (mContactIterator.hasNext())) {
            final ContactItem item = mContactIterator.next();
            final int cmdId = generateContactCmdId();
            AddCommand command = new AddCommand(cmdId);
            command.setOnRPCResponseListener(new OnRPCResponseListener() {
                @Override
                public void onResponse(int correlationId, RPCResponse response) {
                    mMapContactCmdId.put(cmdId, item); // put succeed command id only
                    addContactCommands();
                }
            });
            String json = mGson.toJson(item);
            MenuParams params = new MenuParams();
            params.setMenuName(json);
            command.setMenuParams(params);
            sdlManager.sendRPC(command);
        } else if (mContactList != null){
            mCmdPosIncIndex = 0;
            // reset iterator
            mContactIterator = mContactList.iterator();
            // Send an alert to notify list items finished
            Alert alert = new Alert();
            alert.setAlertText1("CONTACT_FILLED");
            sdlManager.sendRPC(alert);
        }
    }

    private void deleteContactCommands() {
        if ((mMapContactCmdIterator != null) && (mMapContactCmdIterator.hasNext())) {
            final int cmdId = mMapContactCmdIterator.next();
            DeleteCommand command = new DeleteCommand(cmdId);
            command.setOnRPCResponseListener(new OnRPCResponseListener() {
                @Override
                public void onResponse(int correlationId, RPCResponse response) {
                    mMapContactCmdIterator.remove();
                    deleteContactCommands();
                }
            });
            sdlManager.sendRPC(command);
        } else {
            mContactIterator = mContactList.iterator();
            addContactCommands();
        }
    }

    /**
     * Display the phone call history.
     */
    private void createCallLogList() {
        mCallLogMessage = new ArrayList<CallLogItem>();
        Uri uri = Uri.parse("content://call_log/calls");
        Cursor cursor = getContentResolver().query(uri, null ,null,null);

        assert cursor != null;
        if(cursor.moveToFirst()){
            do {
                String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                String name = getNameByPhoneNumber(number);
                if (name.equals(""))
                    name = "Unknown";
                String duration = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION));
                String date = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE)).toString();
                int type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
                if (type > 3)
                    type = 3;
                mCallLogMessage.add( new CallLogItem(name, number, date, duration, type));
                }
            while (cursor.moveToNext());
            }
        else {
            Toast.makeText(getApplicationContext(), "No Call Log to show", Toast.LENGTH_LONG).show();
        }
        if(cursor != null)
            cursor.close();
    }

    private void deletePhoneCommands() {
        if (mMapCmdIterator.hasNext()) {
            final int cmdId = mMapCmdIterator.next();
            DeleteCommand command = new DeleteCommand(cmdId);
            command.setOnRPCResponseListener(new OnRPCResponseListener() {
                @Override
                public void onResponse(int correlationId, RPCResponse response) {
                    mMapCmdIterator.remove();
                    deletePhoneCommands();
                }
            });
            sdlManager.sendRPC(command);
        } else {
            mCallLogIterator = mCallLogMessage.iterator();
            addCallLogCommands();
        }
    }


    private void addCallLogCommands(){
        if (mCallLogIterator.hasNext()) {
            final CallLogItem item = mCallLogIterator.next();
            final int cmdId = generatePhoneCmdId();
            AddCommand command = new AddCommand(cmdId);
            command.setOnRPCResponseListener(new OnRPCResponseListener() {
                @Override
                public void onResponse(int correlationId, RPCResponse response) {
                    mMapCallLogCmdId.put(cmdId,item);
                    addCallLogCommands();
                }
            });
            String json = mGson.toJson(item);
            MenuParams params = new MenuParams();
            params.setMenuName(json);
            command.setMenuParams(params);
            sdlManager.sendRPC(command);
        } else {
            mCmdPosIncIndex = 0;
            // reset iterator
            mCallLogIterator = mCallLogMessage.iterator();
            // Send an alert to notify list items finished
            Alert alert = new Alert();
            alert.setAlertText1("CALL_LOG_FILLED");
            sdlManager.sendRPC(alert);
        }
    }

    public void showCallLogList() {
        if (mMapCallLogCmdId.size() > 0) {
            mMapCmdIterator = mMapCallLogCmdId.keySet().iterator();
            deletePhoneCommands();
        } else {
            if ((mCallLogMessage != null) && (mCallLogMessage.size() > 0)) {
                mCallLogIterator = mCallLogMessage.iterator();
                addCallLogCommands();
            }
            else {
                Alert alert = new Alert();
                alert.setAlertText1("CALL_LOG_FILLED");
                sdlManager.sendRPC(alert);
            }
        }
    }

    public String getNameByPhoneNumber(String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(phoneNumber));
        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};
        String contactName = "";
        Cursor cursor = getContentResolver().query(uri,projection,null,null,null);
        if (cursor != null) {
            if(cursor.moveToFirst()) {
                contactName = cursor.getString(0);
            }
            cursor.close();
        }
        return contactName;
    }

    private int generateContactCmdId() {
        mContactCommandId++;
        if (mContactCommandId >= 1000) {
            mContactCommandId = 500;
        }
        return mContactCommandId;
    }

    private int generatePhoneCmdId() {
        mPhoneCommandId++;
        if (mPhoneCommandId >= 2000) {
            mPhoneCommandId = 1000;
        }
        return mPhoneCommandId;
    }

    private int generateSMSCmdId() {
        mSMSCommandId++;
        if (mSMSCommandId >= 3000) {
            mSMSCommandId = 2000;
        }
        return mSMSCommandId;
    }
}
