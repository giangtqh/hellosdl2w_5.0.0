package com.example.hellosdl2w.callservice;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.Call;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import com.example.hellosdl2w.R;
import com.example.hellosdl2w.SdlService;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import kotlin.collections.CollectionsKt;

import static com.example.hellosdl2w.callservice.Constants.asString;

public class CallActivity extends AppCompatActivity {

    @BindView(R.id.answer)
    Button answer;
    @BindView(R.id.hangup)
    Button hangup;
    @BindView(R.id.callInfo)
    TextView callInfo;


    private CompositeDisposable disposables;
    private String number;
    private OngoingCall ongoingCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        ButterKnife.bind(this);

        ongoingCall = new OngoingCall();
        disposables = new CompositeDisposable();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        number = Objects.requireNonNull(getIntent().getData()).getSchemeSpecificPart();
    }

    @OnClick(R.id.answer)
    public void onAnswerClicked() {
        ongoingCall.answer();
    }

    @OnClick(R.id.hangup)
    public void onHangupClicked() {
        ongoingCall.hangup();
    }



    @Override
    protected void onStart() {
        super.onStart();

//        assert updateUi(-1) != null;
        disposables.add(
                OngoingCall.state
                        .subscribe(new Consumer<Integer>() {
                            @Override
                            public void accept(Integer integer) throws Exception {
                                Log.d("SHIT", "accept");
                                updateUi(integer);
                            }
                        }));

        disposables.add(OngoingCall.state.filter(new Predicate<Integer>() {
            @Override
            public boolean test(Integer integer) throws Exception {
                return integer == Call.STATE_ACTIVE;
            }
        })
                .firstElement()
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        Log.d("SHIT", "SHIT: SHIT");
                        SdlService instanceHook = SdlService.getInstance();
                        if (instanceHook != null) {
                            instanceHook.onStartCall();
                        }
                    }
                }));

        disposables.add(
                OngoingCall.state
                        .filter(new Predicate<Integer>() {
                            @Override
                            public boolean test(Integer integer) throws Exception {
                                return integer == Call.STATE_DISCONNECTED;
                            }
                        })
                        .delay(1, TimeUnit.SECONDS)
                        .firstElement()
                        .subscribe(new Consumer<Integer>() {
                            @Override
                            public void accept(Integer integer) throws Exception {
                                finish();
                            }
                        }));

    }

    @SuppressLint("SetTextI18n")
    private Consumer<? super Integer> updateUi(Integer state) {
        Log.d("SHIT", "updateUi state: " + asString(state));
        callInfo.setText(asString(state) + "\n" + number);

        if (state != Call.STATE_RINGING) {
            answer.setVisibility(View.GONE);
        } else answer.setVisibility(View.VISIBLE);

        if (CollectionsKt.listOf(new Integer[]{
                Call.STATE_DIALING,
                Call.STATE_RINGING,
                Call.STATE_ACTIVE}).contains(state)) {
            hangup.setVisibility(View.VISIBLE);
        } else
            hangup.setVisibility(View.GONE);

        return null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        disposables.clear();
    }

    public static void start(Context context, Call call) {
        Intent intent = new Intent(context, CallActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(call.getDetails().getHandle());
        context.startActivity(intent);
    }
    public static void makeCall(Context context, String phoneNumber) {

        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + phoneNumber));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        context.startActivity(intent);
    }

}
