package com.hss01248.camerax;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import java.util.ArrayList;
import java.util.List;


/**
 * @Despciption todo
 * @Author hss
 * @Date 07/11/2022 10:41
 * @Version 1.0
 */
public class CameraxInit implements  Initializer<String> {


    private boolean checkInit() {
        return false;
    }





    @NonNull
    @Override
    public String create(@NonNull Context context) {

        return "camerax";
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return new ArrayList<>();
    }
}
