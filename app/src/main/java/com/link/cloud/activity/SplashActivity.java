package com.link.cloud.activity;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.link.cloud.Constants;
import com.link.cloud.R;
import com.link.cloud.base.BaseActivity;
import com.link.cloud.bean.DeviceInfo;
import com.link.cloud.controller.MainController;
import com.link.cloud.network.HttpConfig;
import com.link.cloud.network.bean.APPVersionBean;
import com.link.cloud.network.bean.AllUser;
import com.link.cloud.network.bean.BindUser;
import com.link.cloud.network.bean.CabinetInfo;
import com.link.cloud.network.bean.CabnetDeviceInfoBean;
import com.link.cloud.utils.NettyClientBootstrap;
import com.link.cloud.utils.TTSUtils;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;

/**
 * Created by 49488 on 2018/10/20.
 */

public class SplashActivity extends BaseActivity implements MainController.MainControllerListener {

    private NettyClientBootstrap nettyClientBootstrap;
    int pageNum, total;
    private MainController mainController;
    private DeviceInfo deviceInfo;


    @Override
    protected void initViews() {
        pageNum = 100;
        mainController = new MainController(this);
        getDeviceInfo();
        showDate();
        TTSUtils.getInstance().speak("");
    }

    private void showDate() {
        if (deviceInfo != null && deviceInfo.getPsw() != null && !TextUtils.isEmpty(deviceInfo.getPsw())) {
            if (deviceInfo.getToken() != null && !TextUtils.isEmpty(deviceInfo.getToken())) {
                HttpConfig.TOKEN = deviceInfo.getToken();
                getData();
                nettyClientBootstrap = new NettyClientBootstrap(this, Constants.TCP_PORT, Constants.TCP_URL, "{\"data\":{},\"msgType\":\"HEART_BEAT\",\"token\":\"" + deviceInfo.getToken() + "\"}");
                nettyClientBootstrap.start();
            } else {
                getToken();
            }
        } else {
            skipActivity(SettingActivity.class);
        }
    }

    private void getDeviceInfo() {
        final RealmResults<DeviceInfo> all = realm.where(DeviceInfo.class).findAll();
        if (!all.isEmpty()) {
            deviceInfo = all.get(0);
        }
        if (null != deviceInfo)
            HttpConfig.TOKEN = deviceInfo.getToken();
    }


    private void getToken() {
        final RealmResults<AllUser> peopleIn = realm.where(AllUser.class).equalTo("isIn",1).findAll();
        for(int x=0;x<peopleIn.size();x++){
            final int finalX = x;
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    AllUser person = peopleIn.get(finalX);
                    person.setIsIn(0);
                }
            });
        }
        mainController.login(deviceInfo.getDeviceId().trim(), deviceInfo.getPsw());
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_splash;
    }

    @Override
    public void gotoSetting(String pass) {

    }

    @Override
    public void modelMsg(int state, String msg) {

    }

    @Override
    public void onLoginSuccess(final CabnetDeviceInfoBean cabnetDeviceInfoBean) {
        final RealmResults<DeviceInfo> all = realm.where(DeviceInfo.class).findAll();
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                DeviceInfo device = all.get(0);
                device.setToken(cabnetDeviceInfoBean.getToken());
                device.setDeviceTypeId(cabnetDeviceInfoBean.getDeviceInfo().getDeviceTypeId());
                deviceInfo = device;
            }
        });
        HttpConfig.TOKEN = cabnetDeviceInfoBean.getToken();
        getData();
        nettyClientBootstrap = new NettyClientBootstrap(this, Constants.TCP_PORT, Constants.TCP_URL, "{\"data\":{},\"msgType\":\"HEART_BEAT\",\"token\":\"" + deviceInfo.getToken() + "\"}");
        nettyClientBootstrap.start();
    }

    @Override
    public void onMainErrorCode(String msg) {
        if (msg.equals("400000100000") ) {
            skipActivity(SettingActivity.class);
            TTSUtils.getInstance().speak(getString(R.string.login_fail));
        }else if(msg.equals("400000999102")){
            HttpConfig.TOKEN = "";
            getToken();
        }else {
            showNext();
        }


    }

    @Override
    public void onMainFail(Throwable e, boolean isNetWork) {

    }

    boolean isDeleteAll = false;

    @Override
    public void getUserSuccess(final BindUser data) {
        final RealmResults<AllUser> all = realm.where(AllUser.class).findAll();
        total = data.getTotal();
        if (all.size() != data.getTotal()) {
            if (!isDeleteAll) {
                realm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        all.deleteAllFromRealm();
                        isDeleteAll = true;

                    }
                });
                int totalPage = total / pageNum + 1;
                ExecutorService executorService = Executors.newFixedThreadPool(totalPage);
                List<Future<Boolean>> futures = new ArrayList();
                if (totalPage >= 2) {
                    for (int i = 2; i <= totalPage; i++) {
                        final int finalI = i;
                        Callable<Boolean> task = new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                mainController.getUser(pageNum, finalI);
                                return true;
                            }
                        };

                        futures.add(executorService.submit(task));
                    }
                    for (Future<Boolean> future : futures) {
                        try {
                            future.get();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (ExecutionException e) {
                            e.printStackTrace();
                        }
                    }
                    executorService.shutdown();
                }
            }
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    realm.copyToRealm(data.getData());
                }
            });
            Logger.e(data.getData().get(0).getUuid());
            showNext();
        } else {
            showNext();
        }

    }

    public void showNext() {
        Bundle bundle = new Bundle();
        Constants.CABINET_TYPE = deviceInfo.getDeviceTypeId();
        if (deviceInfo.getDeviceTypeId() == Constants.REGULAR_CABINET) {
            bundle.putString(Constants.ActivityExtra.TYPE, "regularactivity");
            skipActivity(RegularActivity.class, bundle);
            finish();
        } else if (deviceInfo.getDeviceTypeId() == Constants.VIP_CABINET) {
            bundle.putString(Constants.ActivityExtra.TYPE, "VipActivity");
            skipActivity(VipActivity.class, bundle);
            finish();
        } else if (deviceInfo.getDeviceTypeId() == Constants.VIP_REGULAR_CABINET) {
            skipActivity(MainActivity.class);
            finish();
        } else {
            skipActivity(SettingActivity.class);
            finish();
            HttpConfig.TOKEN = "";
            Toast.makeText(this, getString(R.string.error_type), Toast.LENGTH_LONG).show();
        }
    }

    private void getData() {
        mainController.getCabinetInfo();
        mainController.getAppVersion();
    }

    @Override
    public void onCabinetInfoSuccess(final RealmList<CabinetInfo> data) {
        final RealmResults<CabinetInfo> cabinetInfoRealmList = realm.where(CabinetInfo.class).findAll();
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                cabinetInfoRealmList.deleteAllFromRealm();
                realm.copyToRealm(data);
            }
        });
        mainController.getUser(pageNum, 1);
    }

    @Override
    public void getVersionSuccess(APPVersionBean appVersionBean) {
        String version = appVersionBean.getVersion();
        try {
            int i = Integer.parseInt(version);
            if(i>getVersion(this)){
                mainController.downloadFile();
            }
        }catch (Exception e)
        {

        }
    }
    private static int getVersion(Context context)// 获取版本号
    {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return 0;
        }
    }
    @Override
    public void temCabinetSuccess(CabinetInfo cabinetBean) {

    }

}
