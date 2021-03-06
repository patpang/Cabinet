package com.link.cloud.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.link.cloud.CabinetApplication;
import com.link.cloud.Constants;
import com.link.cloud.R;
import com.link.cloud.base.BaseActivity;
import com.link.cloud.controller.RegularController;
import com.link.cloud.network.BaseEntity;
import com.link.cloud.network.BaseObserver;
import com.link.cloud.network.IOMainThread;
import com.link.cloud.network.RetrofitFactory;
import com.link.cloud.network.bean.AllUser;
import com.link.cloud.utils.RxTimerUtil;
import com.link.cloud.widget.InputPassWordDialog;
import com.link.cloud.widget.PublicTitleView;
import com.orhanobut.logger.Logger;
import com.zitech.framework.utils.ToastMaster;
import com.zitech.framework.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * 作者：qianlu on 2018/10/10 11:13
 * 邮箱：zar.l@qq.com
 * 选择开柜方式
 */
@SuppressLint("Registered")
public class RegularActivity extends BaseActivity implements RegularController.RegularControllerListener {


    private LinearLayout zhijingmaiLayout;
    private LinearLayout xiaochengxuLayout;
    private TextView passwordLayout;
    private PublicTitleView publicTitleView;
    private RxTimerUtil rxTimerUtil;
    private RegularController regularController;
    private LinearLayout setLayout;
    private TextView member;
    private TextView manager;
    private EditText editText;
    private String mType;
    private boolean isScanning = false;
    private boolean canGetCode;


    @Override
    protected void initViews() {
        rxTimerUtil = new RxTimerUtil();
        zhijingmaiLayout = findViewById(R.id.zhijingmaiLayout);
        xiaochengxuLayout = findViewById(R.id.xiaochengxuLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        publicTitleView = findViewById(R.id.publicTitle);
        setLayout = (LinearLayout) findViewById(R.id.setLayout);
        member = (TextView) findViewById(R.id.member);
        manager = (TextView) findViewById(R.id.manager);
        editText = findViewById(R.id.infoId);
        if (getIntent() != null)
            mType = getIntent().getExtras().getString(Constants.ActivityExtra.TYPE);

        if (!TextUtils.isEmpty(mType)) {
            publicTitleView.setFinsh(View.GONE);
        }

        regularController = new RegularController(this);
        ViewUtils.setOnClickListener(zhijingmaiLayout, this);
        ViewUtils.setOnClickListener(xiaochengxuLayout, this);
        ViewUtils.setOnClickListener(passwordLayout, this);
        ViewUtils.setOnClickListener(manager, this);
        ViewUtils.setOnClickListener(setLayout, this);
        finger();
        publicTitleView.setItemClickListener(new PublicTitleView.onItemClickListener() {
            @Override
            public void itemClickListener() {
                finish();
            }
        });
        if (!TextUtils.isEmpty(getIntent().getStringExtra(Constants.ActivityExtra.TYPE)) && getIntent().getStringExtra(Constants.ActivityExtra.TYPE).equals("REGULAR")) {
            setLayout.setVisibility(View.GONE);
        }
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ToastMaster.shortToast("1" + "start=" + start + "before=" + before + "count=" + count);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(editText.getText().toString().trim())) {
                    rxTimerUtil.timer(1000, new RxTimerUtil.IRxNext() {
                        @Override
                        public void doNext(long number) {
                            unlocking(editText.getText().toString().trim(), Constants.ActivityExtra.XIAOCHENGXU);
                            editText.setText("");
                        }
                    });
                }
            }
        });

    }

    private void finger() {
        rxTimerUtil.interval(1000, new RxTimerUtil.IRxNext() {
            @Override
            public void doNext(long number) {
                System.out.println(String.valueOf(number));
                if (isScanning) {

                    int state = CabinetApplication.getVenueUtils().getState();
                    if (state == 3) {
                        long startTime=System.currentTimeMillis();   //获取开始时间
                        RealmResults<AllUser> users = realm.where(AllUser.class).findAll();
                        List<AllUser> peoples = new ArrayList<>();
                        peoples.addAll(realm.copyFromRealm(users));
                        String uid = CabinetApplication.getVenueUtils().identifyNewImg(peoples);
                        long endTime=System.currentTimeMillis(); //获取结束时间
                        System.out.println("程序运行时间： "+(endTime-startTime)+"ms");
                        if (uid == null) {
                            Logger.e("贾工要的信息+Person:uid=get img failed, please try again ");
                            speak(getResources().getString(R.string.move_finger));
                        }
                        final AllUser uuid = realm.where(AllUser.class).equalTo("uid", uid).findFirst();
                        if (null != uuid) {
                            isScanning=false;
                            unlocking(uuid.getUuid(), Constants.ActivityExtra.FINGER);
                            System.out.println("贾工要的信息+Person:uid=" + uuid.getUuid());
                        } else {
                            speak(getResources().getString(R.string.cheack_fail));
                        }
                    } else if (state == 4) {
                        speak(getResources().getString(R.string.move_finger));
                    }
                }
            }
        });
    }

    private void unlocking(String uid, String type) {
        if (type.equals(Constants.ActivityExtra.FINGER)) {
            speak(getResources().getString(R.string.finger_success));
        } else if (type.equals(Constants.ActivityExtra.XIAOCHENGXU)) {
            speak(getResources().getString(R.string.code_success));
        } else {
            speak(getResources().getString(R.string.password_success));
        }
        Bundle bundle = new Bundle();
        bundle.putString(Constants.ActivityExtra.TYPE, type);
        bundle.putString(Constants.ActivityExtra.UUID, uid);
        showActivity(RegularOpenActivity.class, bundle);
        if (TextUtils.isEmpty(mType)) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isScanning = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isScanning = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        rxTimerUtil.cancel();
    }


    @Override
    protected int getLayoutId() {
        return R.layout.activity_regular;
    }


    @Override
    public void onClick(View v) {
        super.onClick(v);
        switch (v.getId()) {
            case R.id.zhijingmaiLayout:

                break;
            case R.id.xiaochengxuLayout:

                break;
            case R.id.passwordLayout:
                Bundle bundle1 = new Bundle();
                bundle1.putString(Constants.ActivityExtra.TYPE, "PASSWORD");
                showActivity(RegularOpenSuccessActivity.class, bundle1);
                break;

            case R.id.manager:
                showPasswordDialog();
                break;
        }
    }

    @Override
    public void gotoSetting(String pass) {

    }

    private void showPasswordDialog() {
        speak(getResources().getString(R.string.please_save));
        final InputPassWordDialog inputPassWordDialog = new InputPassWordDialog(this);
        inputPassWordDialog.setCheakListener(new InputPassWordDialog.CheakListener() {
            @Override
            public void inputCheakSuccess(String pwd) {

                RetrofitFactory.getInstence().API().validatePassword(pwd).compose(IOMainThread.<BaseEntity>composeIO2main()).subscribe(new BaseObserver() {
                    @Override
                    protected void onSuccees(BaseEntity t) {
                        showActivity(SettingActivity.class);
                        inputPassWordDialog.dismiss();
                    }

                    @Override
                    protected void onCodeError(String msg, String codeErrorr) {
                        speak(msg);
                        inputPassWordDialog.dismiss();
                    }

                    @Override
                    protected void onFailure(Throwable e, boolean isNetWorkError) {
                        inputPassWordDialog.dismiss();
                    }
                });

            }

            @Override
            public void inputCheakFail() {

            }
        });
        inputPassWordDialog.show();
        rxTimerUtil.timer(20000, new RxTimerUtil.IRxNext() {
            @Override
            public void doNext(long number) {
                if (null != inputPassWordDialog && inputPassWordDialog.isShowing()) {
                    inputPassWordDialog.dismiss();
                }
            }
        });

    }


    @Override
    public void modelMsg(int state, String msg) {

    }


    @Override
    public void successful(final AllUser allUser) {
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                realm.insert(allUser);
            }
        });
        speak(getResources().getString(R.string.finger_success));
        Bundle bundle = new Bundle();
        bundle.putString(Constants.ActivityExtra.TYPE, Constants.ActivityExtra.FINGER);
        bundle.putString(Constants.ActivityExtra.UUID, allUser.getUuid());
        showActivity(RegularOpenActivity.class, bundle);
        if (TextUtils.isEmpty(mType)) {
            finish();
        }
    }

    @Override
    public void faild(String message) {
        speak(message);
    }

    @Override
    public void onRegularFail(Throwable e, boolean isNetWork) {

    }
}
