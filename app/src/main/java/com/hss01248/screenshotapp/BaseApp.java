package com.hss01248.screenshotapp;

import androidx.multidex.MultiDexApplication;

import com.blankj.utilcode.util.ThreadUtils;
import com.blankj.utilcode.util.Utils;


/**
 * @Despciption todo
 * @Author hss
 * @Date 5/10/24 5:49 PM
 * @Version 1.0
 */
public class BaseApp extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.init(this);

        //crash();
    }

    private void crash() {
        ThreadUtils.getMainHandler().postDelayed(new Runnable(){

            @Override
            public void run() {
                int i = 1/0;
            }
        },2000);
    }
}
