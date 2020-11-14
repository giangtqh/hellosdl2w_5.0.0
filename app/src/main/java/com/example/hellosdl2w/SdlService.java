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
import android.util.Log;
import android.widget.Toast;

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
import com.smartdevicelink.proxy.rpc.enums.SpeechCapabilities;
import com.smartdevicelink.proxy.rpc.enums.TextAlignment;
import com.smartdevicelink.proxy.rpc.enums.TriggerSource;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TCPTransportConfig;
import com.smartdevicelink.util.CorrelationIdGenerator;
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
    private static final String SDL_IMAGE_FILENAME = "sdl_full_image.png";

    private static final String WELCOME_SHOW = "Welcome to HelloSDL";
    private static final String WELCOME_SPEAK = "Welcome to Hello S D L";

    private static final String TEST_COMMAND_NAME = "Test Command";

    private static final int FOREGROUND_SERVICE_ID = 111;

    // TCP/IP transport config
    // The default port is 12345
    // The IP is of the machine that is running SDL Core
    private static final int TCP_PORT = 12345;
    private static final String DEV_MACHINE_IP_ADDRESS = "m.sdl.tools";

    // variable to create and call functions of the SyncProxy
    private SdlManager sdlManager = null;
    private List<ChoiceCell> choiceCellList;
    private List<ChoiceCell> mChoiceContactList;
    private List<ChoiceCell> mChoiceSMSList;

    private HMILevel currentHMILevel = HMILevel.HMI_NONE;
    private static final int PHONE_BTN_ID = 100;
    private static final int CONTACT_BTN_ID = 101;
    private static final int SMS_BTN_ID = 102;
    // Choice set for Phone, Contact, SMS (this replace for SoftButton
    private static final int CHOICE_PHONE_ID = 200;
    private static final int CHOICE_CONTACT_ID = 201;
    private static final int CHOICE_SMS_ID = 202;

    // Contact commands ID range from 500->999, Phone: 1000->1999, SMS: 2000->2999
    private static int mContactCommandId = 500;
    private static int mPhoneCommandId = 1000;
    private static int mSMSCommandId = 2000;
    // old
//	private SoftButton mPhoneSoftBtn = null;
//	private SoftButton mContactSoftBtn = null;
//	private SoftButton mSMSSoftBtn = null;
    // 4.7
    private SoftButtonObject mPhoneSoftBtn = null;
    private SoftButtonObject mContactSoftBtn = null;
    private SoftButtonObject mSMSSoftBtn = null;

    private int mWelcomeCorrId;
    private int mSNSAlertId;
    private int mMenuChoiceSetId;
    private InfoType mActiveInfoType = InfoType.NONE; // Stores the current type of information being displayed
    private static SdlService instance = null;

    List<SMSMessage> mSMSMessages = null;
    Iterator<SMSMessage> mSMSIterator = null;
    Map<Integer, SMSMessage> mMapSMSCmdId = new HashMap<>();
    Iterator<Integer> mMapCmdIterator = null;
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

        SoftButtonState mShowPhoneState = new SoftButtonState("mShowPhoneState", getResources().getString(R.string.phone), null);
        mPhoneSoftBtn = new SoftButtonObject("mPhoneSoftBtn", Collections.singletonList(mShowPhoneState), mShowPhoneState.getName(), new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                mActiveInfoType = InfoType.PHONE;
                Toast.makeText(getApplicationContext(), "Phone clicked", Toast.LENGTH_LONG).show();
                // TODO (Toan): Get call history, create choice set
                updateHmi();
            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        });
        mPhoneSoftBtn.setButtonId(PHONE_BTN_ID);

        SoftButtonState mShowContactState = new SoftButtonState("mShowContactState", getResources().getString(R.string.contact), null);
        mContactSoftBtn = new SoftButtonObject("mContactSoftBtn", Collections.singletonList(mShowContactState), mShowContactState.getName(), new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                mActiveInfoType = InfoType.CONTACT;
                Toast.makeText(getApplicationContext(), "Contact clicked", Toast.LENGTH_LONG).show();
                createContactChoiceSet();
                updateHmi();
            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

            }
        });
        mContactSoftBtn.setButtonId(CONTACT_BTN_ID);

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

//		mPhoneSoftBtn = new SoftButton();
//		mPhoneSoftBtn.setSoftButtonID(PHONE_BTN_ID);
//		mPhoneSoftBtn.setText(getResources().getString(R.string.phone));
//		mPhoneSoftBtn.setType(SoftButtonType.SBT_TEXT);
//		mPhoneSoftBtn.setIsHighlighted(false);
//		mPhoneSoftBtn.setSystemAction(SystemAction.DEFAULT_ACTION);
//
//		mContactSoftBtn = new SoftButton();
//		mContactSoftBtn.setSoftButtonID(CONTACT_BTN_ID);
//		mContactSoftBtn.setText(getResources().getString(R.string.contact));
//		mContactSoftBtn.setType(SoftButtonType.SBT_TEXT);
//		mContactSoftBtn.setIsHighlighted(false);
//		mContactSoftBtn.setSystemAction(SystemAction.DEFAULT_ACTION);
//
//		mSMSSoftBtn = new SoftButton();
//		mSMSSoftBtn.setSoftButtonID(SMS_BTN_ID);
//		mSMSSoftBtn.setText(getResources().getString(R.string.sms));
//		mSMSSoftBtn.setType(SoftButtonType.SBT_TEXT);
//		mSMSSoftBtn.setIsHighlighted(false);
//		mSMSSoftBtn.setSystemAction(SystemAction.DEFAULT_ACTION);
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
                                //startAudioStream();
                            }
                        }
                    });

                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_COMMAND, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnCommand onCommand = (OnCommand) notification;
                            int cmdId = onCommand.getCmdID();
                            Toast.makeText(getApplicationContext(), "onCommand Id: " + cmdId, Toast.LENGTH_LONG).show();
                            if (cmdId < 1000) {
                                // Contact list commands
                            } else if (cmdId < 2000) {
                                // Call log commands
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
     * Send some voice commands
     */
    private void setVoiceCommands() {

        List<String> list1 = Collections.singletonList("Command One");
        List<String> list2 = Collections.singletonList("Command two");

        VoiceCommand voiceCommand1 = new VoiceCommand(list1, new VoiceCommandSelectionListener() {
            @Override
            public void onVoiceCommandSelected() {
                Log.i(TAG, "Voice Command 1 triggered");
            }
        });

        VoiceCommand voiceCommand2 = new VoiceCommand(list2, new VoiceCommandSelectionListener() {
            @Override
            public void onVoiceCommandSelected() {
                Log.i(TAG, "Voice Command 2 triggered");
            }
        });

        sdlManager.getScreenManager().setVoiceCommands(Arrays.asList(voiceCommand1, voiceCommand2));
    }

    /**
     * Add menus for the app on SDL.
     */
    private void sendMenus() {

        // some arts
        SdlArtwork livio = new SdlArtwork("livio", FileType.GRAPHIC_PNG, R.drawable.sdl, false);

        // some voice commands
        List<String> voice2 = Collections.singletonList("Cell two");

        MenuCell mainCell1 = new MenuCell("Test Cell 1 (speak)", livio, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                Log.i(TAG, "Test cell 1 triggered. Source: " + trigger.toString());
                showTest();
            }
        });

        MenuCell mainCell2 = new MenuCell("Test Cell 2", null, voice2, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                Log.i(TAG, "Test cell 2 triggered. Source: " + trigger.toString());
            }
        });

        // SUB MENU

        MenuCell subCell1 = new MenuCell("SubCell 1", null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                Log.i(TAG, "Sub cell 1 triggered. Source: " + trigger.toString());
            }
        });

        MenuCell subCell2 = new MenuCell("SubCell 2", null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                Log.i(TAG, "Sub cell 2 triggered. Source: " + trigger.toString());
            }
        });

        // sub menu parent cell
        MenuCell mainCell3 = new MenuCell("Test Cell 3 (sub menu)", MenuLayout.LIST, null, Arrays.asList(subCell1, subCell2));

        MenuCell mainCell4 = new MenuCell("Show Perform Interaction", null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                showPerformInteraction();
            }
        });

        MenuCell mainCell5 = new MenuCell("Clear the menu", null, null, new MenuSelectionListener() {
            @Override
            public void onTriggered(TriggerSource trigger) {
                Log.i(TAG, "Clearing Menu. Source: " + trigger.toString());
                // Clear this thing
                sdlManager.getScreenManager().setMenu(Collections.<MenuCell>emptyList());
                showAlert("Menu Cleared");
            }
        });

        // Send the entire menu off to be created
        sdlManager.getScreenManager().setMenu(Arrays.asList(mainCell1, mainCell2, mainCell3, mainCell4, mainCell5));
    }

    /**
     * Will speak a sample welcome message
     */
    private void performWelcomeSpeak() {
        List<TTSChunk> chunks = Collections.singletonList(new TTSChunk(WELCOME_SPEAK, SpeechCapabilities.TEXT));
        sdlManager.sendRPC(new Speak(chunks));
    }

    /**
     * Use the Screen Manager to set the initial screen text and set the image.
     * Because we are setting multiple items, we will call beginTransaction() first,
     * and finish with commit() when we are done.
     */
    private void performWelcomeShow() {
        sdlManager.getScreenManager().beginTransaction();
        sdlManager.getScreenManager().setTextField1(APP_NAME);
        sdlManager.getScreenManager().setTextField2(WELCOME_SHOW);
        sdlManager.getScreenManager().setPrimaryGraphic(new SdlArtwork(SDL_IMAGE_FILENAME, FileType.GRAPHIC_PNG, R.drawable.sdl, true));
        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                if (success) {
                    Log.i(TAG, "welcome show successful");
                }
            }
        });
    }

    /**
     * Attempts to Subscribe to all preset buttons
     */
    private void subscribeToButtons() {
        ButtonName[] buttonNames = {ButtonName.PLAY_PAUSE, ButtonName.SEEKLEFT, ButtonName.SEEKRIGHT, ButtonName.AC_MAX, ButtonName.AC, ButtonName.RECIRCULATE,
                ButtonName.FAN_UP, ButtonName.FAN_DOWN, ButtonName.TEMP_UP, ButtonName.TEMP_DOWN, ButtonName.FAN_DOWN, ButtonName.DEFROST_MAX, ButtonName.DEFROST_REAR, ButtonName.DEFROST,
                ButtonName.UPPER_VENT, ButtonName.LOWER_VENT, ButtonName.VOLUME_UP, ButtonName.VOLUME_DOWN, ButtonName.EJECT, ButtonName.SOURCE, ButtonName.SHUFFLE, ButtonName.REPEAT};

        OnButtonListener onButtonListener = new OnButtonListener() {
            @Override
            public void onPress(ButtonName buttonName, OnButtonPress buttonPress) {
                sdlManager.getScreenManager().setTextField1(buttonName + " pressed");
            }

            @Override
            public void onEvent(ButtonName buttonName, OnButtonEvent buttonEvent) {
                sdlManager.getScreenManager().setTextField2(buttonName + " " + buttonEvent.getButtonEventMode());
            }

            @Override
            public void onError(String info) {
                Log.i(TAG, "onError: " + info);
            }
        };

        for (ButtonName buttonName : buttonNames) {
            sdlManager.getScreenManager().addButtonListener(buttonName, onButtonListener);
        }
    }

    /**
     * Will show a sample test message on screen as well as speak a sample test message
     */
    private void showTest() {
        sdlManager.getScreenManager().beginTransaction();
        sdlManager.getScreenManager().setTextField1("Test Cell 1 has been selected");
        sdlManager.getScreenManager().setTextField2("");
        sdlManager.getScreenManager().commit(null);

        List<TTSChunk> chunks = Collections.singletonList(new TTSChunk(TEST_COMMAND_NAME, SpeechCapabilities.TEXT));
        sdlManager.sendRPC(new Speak(chunks));
    }

    private void showAlert(String text) {
        Alert alert = new Alert();
        alert.setAlertText1(text);
        alert.setDuration(5000);
        sdlManager.sendRPC(alert);
    }

    // Choice Set

    private void preloadChoices() {
        ChoiceCell cell1 = new ChoiceCell("Item 1");
        ChoiceCell cell2 = new ChoiceCell("Item 2");
        ChoiceCell cell3 = new ChoiceCell("Item 3");
        choiceCellList = new ArrayList<>(Arrays.asList(cell1, cell2, cell3));
        sdlManager.getScreenManager().preloadChoices(choiceCellList, null);
    }

    private void showPerformInteraction() {
        if (choiceCellList != null) {
            ChoiceSet choiceSet = new ChoiceSet("Choose an Item from the list", choiceCellList, new ChoiceSetSelectionListener() {
                @Override
                public void onChoiceSelected(ChoiceCell choiceCell, TriggerSource triggerSource, int rowIndex) {
                    showAlert(choiceCell.getText() + " was selected");
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "There was an error showing the perform interaction: " + error);
                }
            });
            sdlManager.getScreenManager().presentChoiceSet(choiceSet, InteractionMode.MANUAL_ONLY);
        }
    }

    /**
     * Shows and speaks a welcome message
     */
    private void showFeatures() {
        // 4.7 change SoftButton to SoftButtonObject
        Vector<SoftButtonObject> softButtons = new Vector<SoftButtonObject>();
        softButtons.add(mPhoneSoftBtn);
        softButtons.add(mContactSoftBtn);
        softButtons.add(mSMSSoftBtn);

        // 4.6 still work
//		Show showRequest = new Show();
//		showRequest.setMainField1(getResources().getString(R.string.app_main_field1));
//		showRequest.setMainField2(getResources().getString(R.string.app_main_field2));
//		showRequest.setMainField3(getResources().getString(R.string.app_main_field3));
//		showRequest.setMainField4(getResources().getString(R.string.welcome_textfield4));
//		showRequest.setAlignment(TextAlignment.CENTERED);
//		mWelcomeCorrId = CorrelationIdGenerator.generateId();
//		showRequest.setCorrelationID(mWelcomeCorrId);
//		showRequest.setSoftButtons(softButtons);
////			proxy.sendRPCRequest(showRequest);
//		sdlManager.sendRPC(showRequest);

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
                performShowCallLog();
                break;
            case CONTACT:
                performShowContacts();
                break;
            case SMS:
                //performShowSMS();
                showSMSList();
                break;
            default:
                break;
        }
    }

    // TODO(GTR): use Alert request for SMS notification, check why this not work?
    // Alert has 3 mainField, use mainField1 for messageType, mainField2 for message body
    // Alert message type:
    // ON_CALL, ON_END_CALL, ON_DIAL, ON_SMS
    public void onSMSNotification(CustomAlert alert) {
        Alert alertRequest = new Alert();
        alertRequest.setAlertText1("ON_SMS");
        alertRequest.setAlertText2(alert.number);
        alertRequest.setAlertText3(alert.message);
        alertRequest.setDuration(7000);

//		mSMSAlertId = CorrelationIdGenerator.generateId();
        //alertRequest.setCorrelationID(CorrelationIdGenerator.generateId());
        sdlManager.sendRPC(alertRequest);
        Toast.makeText(this, "SdlService::onSMSNotification(): " + alert.message, Toast.LENGTH_LONG).show();
    }

    public void onInCommingCall(String number) {
        Alert alert = new Alert();
        alert.setAlertText1("ON_CALL");
        alert.setAlertText2(number);
        alert.setDuration(5000);
        alert.setCorrelationID(CorrelationIdGenerator.generateId());
        sdlManager.sendRPC(alert);
        Toast.makeText(this, "Incomming call", Toast.LENGTH_LONG).show();
    }

    public void onDial(String number) {
        Alert alert = new Alert();
        alert.setAlertText1("ON_DIAL");
        alert.setAlertText2(number);
        alert.setDuration(5000);
        alert.setCorrelationID(CorrelationIdGenerator.generateId());
        sdlManager.sendRPC(alert);
        Toast.makeText(this, "Dialing " + number, Toast.LENGTH_LONG).show();
    }

    public void onEndCall() {
        Alert alert = new Alert();
        alert.setAlertText1("ON_END_CALL");
        alert.setDuration(5000);
        alert.setCorrelationID(CorrelationIdGenerator.generateId());
        sdlManager.sendRPC(alert);
        Toast.makeText(this, "End call.", Toast.LENGTH_LONG).show();
    }

    private void createContactChoiceSet() {
        // TODO(Toan): Use real contact list
        List<String> dummyContacts = new ArrayList<>(Arrays.asList(
                "Bui Le Thuan",
                "Nguyen Hoang An",
                "Dinh Cong Toan",
                "Nguyen Thanh Dung",
                "Tran Quang Hoang Giang"
        ));

        mChoiceContactList = new ArrayList<>();
        for (String item : dummyContacts) {
            mChoiceContactList.add(new ChoiceCell(item));
        }
        sdlManager.getScreenManager().preloadChoices(mChoiceContactList, null);
    }

    private void performShowContacts() {
        ChoiceSet choiceSet = new ChoiceSet("Choose an Item from the list", mChoiceContactList, new ChoiceSetSelectionListener() {
            @Override
            public void onChoiceSelected(ChoiceCell choiceCell, TriggerSource triggerSource, int rowIndex) {
                showAlert(choiceCell.getText() + " was selected");
                Toast.makeText(getApplicationContext(), choiceCell.getText() + " was selected", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "There was an error showing the perform interaction: " + error);
            }
        });
        sdlManager.getScreenManager().presentChoiceSet(choiceSet, InteractionMode.MANUAL_ONLY);

    }

    private void createSMSList() {
        mSMSMessages = new ArrayList<SMSMessage>();
        // get all sms messages includes inbox and sent
        Uri uriSMSURI = Uri.parse("content://sms/");
        Cursor cursor = getContentResolver().query(uriSMSURI, null, null, null, null);

        if ((cursor != null) && cursor.moveToFirst()) {
            do {
                String body = cursor.getString(cursor.getColumnIndex("body"));
                String address = cursor.getString(cursor.getColumnIndex("address"));
                String date = cursor.getString(cursor.getColumnIndex("date"));
                int read = cursor.getInt(cursor.getColumnIndex("read"));
                int type = cursor.getInt(cursor.getColumnIndex("type"));
//                for (int idx = 0; idx < cursor.getColumnCount(); idx++) {
//                    body += " " + cursor.getColumnName(idx) + ":" + cursor.getString(idx);
//                }
                mSMSMessages.add(new SMSMessage(address, body, date, read, type));
            } while (cursor.moveToNext());
        } else {
            Toast.makeText(getApplicationContext(), "No SMS", Toast.LENGTH_LONG).show();
        }

        if (cursor != null) {
            cursor.close();
        }
    }

    private void sendCommands() {
        AddCommand command = new AddCommand();
        MenuParams params = new MenuParams();
        params.setMenuName("Giang Test menu AddCommand");
        command.setCmdID(9999);
        command.setMenuParams(params);
        sdlManager.sendRPC(command);
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
        } else if (mSMSMessages.size() > 0) {
            mSMSIterator = mSMSMessages.iterator();
            addSMSCommands();
        }
    }

    private void performShowSMS() {
        ChoiceSet choiceSet = new ChoiceSet("Choose an Item from the list", mChoiceSMSList, new ChoiceSetSelectionListener() {
            @Override
            public void onChoiceSelected(ChoiceCell choiceCell, TriggerSource triggerSource, int rowIndex) {
                //showAlert(choiceCell.getText() + " was selected");
                Toast.makeText(getApplicationContext(), choiceCell.getText() + " was selected", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "There was an error showing the perform interaction: " + error);
            }
        });
        sdlManager.getScreenManager().presentChoiceSet(choiceSet, InteractionMode.MANUAL_ONLY);
    }

    /**
     * Display the phone call history.
     */
    private void performShowCallLog() {
        // TODO(Toan):
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
