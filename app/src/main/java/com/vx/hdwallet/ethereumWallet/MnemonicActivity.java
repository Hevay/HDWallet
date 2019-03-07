package com.vx.hdwallet.ethereumWallet;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.vx.hdwallet.R;
import com.vx.hdwallet.config.Constants;

import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class MnemonicActivity extends AppCompatActivity {

    private EditText mnemonics;
    private EditText address;
    private EditText importMnemonics;
    private EditText importWalletAddress;

    private static final String PASSWORD = Constants.password;

    private WalletFile wallet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mnemonic);
        initView();
    }

    private void initView() {
        mnemonics = findViewById(R.id.mnemonics);
        address = findViewById(R.id.address);
        importMnemonics = findViewById(R.id.import_mnemonics);
        importWalletAddress = findViewById(R.id.import_address);
    }

    public void onCreateMnemonicWallet(View view) {
        try {
            List<String> words = createMnemonics();
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (word.equals(words.get(words.size() - 1))){
                    sb.append(word);
                }else {
                    sb.append(word);
                    sb.append(",");
                }
            }
            mnemonics.setText(sb.toString());
            wallet = createWallet(words);
            String walletAddress = "0x" + wallet.getAddress();
            address.setText(walletAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private WalletFile createWallet(List<String> words) throws MnemonicException.MnemonicLengthException, MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException, CipherException {
        byte[] seeds = MnemonicCode.INSTANCE.toEntropy(words);
        DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey(seeds);
        DeterministicHierarchy deterministicHierarchy = new DeterministicHierarchy(masterPrivateKey);
        DeterministicKey deterministicKey = deterministicHierarchy.deriveChild(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH, true, true, ChildNumber.ZERO);
        ECKeyPair ecKeyPair = ECKeyPair.create(deterministicKey.getPrivKeyBytes());
        return Wallet.createLight(PASSWORD, ecKeyPair);
    }

    private List<String> createMnemonics() throws MnemonicException.MnemonicLengthException {
        //生成12个助记词
        byte[] entropy = new byte[DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8];
        new SecureRandom().nextBytes(entropy);
        return MnemonicCode.INSTANCE.toMnemonic(entropy);
    }

    public void onImportMnemonics(View view) throws CipherException, MnemonicException.MnemonicLengthException, MnemonicException.MnemonicWordException, MnemonicException.MnemonicChecksumException {
        String words = importMnemonics.getText().toString().trim();
        String[] split = words.split(",");
        ArrayList<String> mnemonics = new ArrayList<>();
        for (String s : split) {
            mnemonics.add(s.trim());
        }
        wallet = createWallet(mnemonics);
        String walletAddress = "0x" + wallet.getAddress();
        importWalletAddress.setText(walletAddress);
    }
}
