package com.vx.hdwallet;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.vx.hdwallet.bitcoinWallet.BitcoinWalletActivity;
import com.vx.hdwallet.ethereumWallet.EthereumWalletActivity;

public class MainActivity extends AppCompatActivity{

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onStartBitcoinWallet(View view){
        Log.d(TAG, "onStartBitcoinWallet:   开启了比特币钱包");
        Intent intent = new Intent(this, BitcoinWalletActivity.class);
        startActivity(intent);
    }

    public void onStartEthereumWallet(View view){
        Log.d(TAG, "onStartEthereumWallet:  开启了以太坊钱包");
        Intent intent = new Intent(this, EthereumWalletActivity.class);
        startActivity(intent);
    }
}