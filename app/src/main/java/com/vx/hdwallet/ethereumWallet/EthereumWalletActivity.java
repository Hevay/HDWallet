package com.vx.hdwallet.ethereumWallet;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.vx.hdwallet.config.Constants;
import com.vx.hdwallet.Qr;
import com.vx.hdwallet.R;

import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.wallet.DeterministicSeed;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class EthereumWalletActivity extends AppCompatActivity {
    private static final String TAG = "EthereumWalletActivity";

    //构建web3j对象
    private final Web3j web3j = Web3jFactory.build(new HttpService("https://rinkeby.infura.io/v3/b4e3d3879051411c9b4a198bcd6f08ff"));
    private final String contractAddress = "0x3695bFF0B8D331f066440eCE93e7Dd56078E02Ea";

    private static final String PASSWORD = Constants.password;
    //json文件转换的工具，可以将java对象转换json文件，或者json文件转换成java 对象
    private ObjectMapper objectMapper = new ObjectMapper();
    //  m/44'/60'/0'/0
    public static final ImmutableList<ChildNumber> BIP44_ETH_ACCOUNT_ZERO_PATH =
            ImmutableList.of(new ChildNumber(44, true),
                    new ChildNumber(60, true),
                    ChildNumber.ZERO_HARDENED,
                    ChildNumber.ZERO);

    private TextView mAddress;
    private ImageView mQrImageView;
    private TextView mBalance;
    private EditText mToAddress;
    private EditText mAmount;

    private TextView mTokenBalance;
    private EditText mTokenToAddress;
    private EditText mTokenAmount;

    private WalletFile walletFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ethereum_wallet);
        //初始化各控件
        initView();
        //加载钱包， 异步执行
        AsyncTask.execute(loadWallet);
    }

    private void initView() {
        mAddress = findViewById(R.id.address);
        mQrImageView = findViewById(R.id.qr_code);
        mBalance = findViewById(R.id.balance);
        mToAddress = findViewById(R.id.to_address);
        mAmount = findViewById(R.id.amount);

        mTokenBalance = findViewById(R.id.token_balance);
        mTokenToAddress = findViewById(R.id.token_to_address);
        mTokenAmount = findViewById(R.id.token_amount);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.eth_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            switch (item.getItemId()) {
                case R.id.export_keystore:
                    Intent keystoreIntent = new Intent(this, KeyStoreActivity.class);
                    keystoreIntent.putExtra("keystore", objectMapper.writeValueAsString(walletFile));
                    startActivity(keystoreIntent);
                    break;

                case R.id.export_private_key:

                    ECKeyPair ecKeyPair = Wallet.decrypt(PASSWORD, walletFile);
                    BigInteger privateKey = ecKeyPair.getPrivateKey();
                    String exportKey = Numeric.toHexStringNoPrefixZeroPadded(privateKey, Keys.PRIVATE_KEY_LENGTH_IN_HEX);

                    Intent privateKeyIntent = new Intent(this, PrivateKeyActivity.class);
                    privateKeyIntent.putExtra("pk", exportKey);
                    startActivity(privateKeyIntent);
                    break;

                case R.id.export_mnemonics:
                    Intent mnemonicIntent = new Intent(this, MnemonicActivity.class);
                    startActivity(mnemonicIntent);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return super.onOptionsItemSelected(item);
    }

    //加载钱包的异步线程
    //没有使用助记词，生成的是非确定性钱包
    private Runnable loadWallet = new Runnable() {
        @Override
        public void run() {
            //获取钱包文件
            File dir = getDir("eth", MODE_PRIVATE);
            //比特币的钱包文件是protobuf格式，以太坊钱包文件是json格式
            File file = new File(dir, "eth_wallet.json");
            try {
                //判断钱包文件是否存在
                if (!file.exists()) {
                    //如果钱包文件不存在，创建新的钱包，并序列化存储到文件
                    Log.d(TAG, "run:  创建新的钱包");
                    ECKeyPair ecKeyPair = Keys.createEcKeyPair();
                    //创建新的钱包文件，也就是keystore
                    walletFile = Wallet.createLight(PASSWORD, ecKeyPair);
                    //将钱包以文件的形式存储
                    objectMapper.writeValue(file, walletFile);
                } else {
                    //如果钱包文件已经存在，直接加载
                    Log.d(TAG, "run:   加载已经存在的钱包");
                    walletFile = objectMapper.readValue(file, WalletFile.class);
                }
                Log.d(TAG, "run:  钱包的地址是:  0x" + walletFile.getAddress());
            } catch (Exception e) {
                e.printStackTrace();
            }

            //在主线程更新UI界面
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateUI();
                }
            });

            //刷新钱包中的eth余额
            loadBalance();
            //刷新Token余额
            loadTokenBalance();
        }
    };

    //获取token余额
    private void loadTokenBalance() {
        //获取钱包地址
        String owner = "0x" + walletFile.getAddress();

        //创建Function, 参数1： 方法名
        //                      参数2：合约方法的传参
        //                      参数3：合约方法的返回值类型 uint256
        Function function = new Function("balanceOf", Collections.singletonList(new Address(owner)),
                Collections.singletonList(new TypeReference<Uint256>() {
                }));

        //解析function，以便于调用
        String encode = FunctionEncoder.encode(function);
        //执行智能合约里面的balanceOf方法， 传入调用者地址， 要执行的智能合约的地址， 以及智能合约里面的要执行的函数
        //生成一个交易请求对象 transaction
        Transaction transaction = Transaction.createEthCallTransaction(owner, contractAddress, encode);
        try {
            //调用智能合约
            String result = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send().getValue();
            //解析智能合约返回的结果
            List<Type> decode = FunctionReturnDecoder.decode(result, function.getOutputParameters());

            Log.d(TAG, "loadTokenBalance:  智能合约返回的结果是： " + decode.toString());

            //获取只能合约返回的结果中索引为0的数据，并强转为Uint256类型
            Uint256 type = (Uint256) decode.get(0);
            //把结果转换为BigInteger类型
            BigInteger tokenBalance = type.getValue();
            //转化为String类型
            String tokenBalanceString = String.valueOf(tokenBalance.longValue() / 100) + "MSC";
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTokenBalance.setText(tokenBalanceString);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //获取钱包余额
    private void loadBalance() {
        try {
            //获取钱包余额
            String address = "0x" + walletFile.getAddress();
            BigInteger balance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();

            //将获取到的余额转化为以eth为单位
            BigDecimal balanceEth = Convert.fromWei(balance.toString(), Convert.Unit.ETHER);
            //转化为字符串形式
            String balanceString = balanceEth.toPlainString() + "ETH";
            Log.d(TAG, "loadBalance:  获取到的以太坊的余额是: " + balanceString);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBalance.setText(balanceString);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateUI() {
        String address = "0x" + walletFile.getAddress();
        //测试在主线程更新UI界面时能否获得地址和余额数据

        //生成二维码地址
        Bitmap bitmap = Qr.bitmap(address);
        final BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), bitmap);
        bitmapDrawable.setFilterBitmap(false);

        //更新钱包地址
        mAddress.setText(address);
        //更新二维码
        mQrImageView.setImageDrawable(bitmapDrawable);
    }

    //执行以太坊的交易过程
    public void onSendEth(View view) {
        //获取用户输入的地址
        String toAddress = mToAddress.getText().toString();

        //获取用户输入的金额，注意这里的单位是eth
        String amountString = mAmount.getText().toString();

        //校验用户输入是否为空
        if (TextUtils.isEmpty(toAddress) || TextUtils.isEmpty(toAddress.trim())) {
            Toast.makeText(this, "对不起, 转账地址不能为空", Toast.LENGTH_SHORT).show();
        } else if (TextUtils.isEmpty(amountString) || TextUtils.isEmpty(amountString.trim())) {
            Toast.makeText(this, "对不起, 转账地址不能为空", Toast.LENGTH_SHORT).show();
        } else {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    //将字符串转为为BigDecimal类型， 并将单位转化为wei
                    BigDecimal amountWei = Convert.toWei(amountString, Convert.Unit.ETHER);

                    //获取当前钱包的地址
                    String address = "0x" + walletFile.getAddress();

                    //构建Transaction对象，未签名， 其实是RawTransaction对象
                    //需要传入的四个参数: 1. nonce，当前钱包的总交易次数
                    //                                2. gasPrice, 邮费
                    //                                3. gasLimit, 给定最大邮费，超出这个数额就放弃执行交易
                    //                                4. to, 转账到哪个地址
                    //                                5. value, 转账的金额
                    try {
                        //获取第一个参数nonce
                        BigInteger nonce = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).send().getTransactionCount();

                        //获取第二个参数gasPrice
                        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

                        //第三个参数
                        BigInteger gasLimit = new BigInteger("300000");

                        //第四个参数和第五个参数由用户在界面输入
                        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, toAddress, amountWei.toBigInteger());

                        //进行交易签名的操作

                        //传入当前钱包的密码和钱包文件，进行解密操作，获取创建钱包文件时的密钥对
                        ECKeyPair ecKeyPair = Wallet.decrypt(PASSWORD, walletFile);

                        //使用密钥对生成证书
                        Credentials credentials = Credentials.create(ecKeyPair);

                        //使用证书给原始交易对象进行消息摘要算法， 并返回消息摘要（也就是签名）的字节数组
                        byte[] bytes = TransactionEncoder.signMessage(rawTransaction, credentials);

                        //将返回的字节数组转换为16进制的字符串形式
                        String hexString = Numeric.toHexString(bytes);

                        //使用签名执行交易, 并返回本次交易的哈希值
                        String transactionHash = web3j.ethSendRawTransaction(hexString).send().getTransactionHash();

                        Log.d(TAG, "onSendEth: 本次Rinkeby以太坊交易的哈希值是： " + transactionHash);

                        //刷新eth余额
                        loadBalance();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    //执行Token的转账交易
    public void onSendToken(View view) {
        //获取用户输入的地址和金额
        String toAddress = mTokenToAddress.getText().toString();
        //校验用户输入是否为空
        String amount = mTokenAmount.getText().toString();
        if (TextUtils.isEmpty(toAddress) || TextUtils.isEmpty(toAddress.trim())) {
            Toast.makeText(EthereumWalletActivity.this, "对不起, 转账地址不能为空", Toast.LENGTH_SHORT).show();
        } else if (TextUtils.isEmpty(amount) || TextUtils.isEmpty(amount.trim())) {
            Toast.makeText(EthereumWalletActivity.this, "对不起, 转账金额不能为空", Toast.LENGTH_SHORT).show();
        } else AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String address = "0x" + walletFile.getAddress();
                    //创建智能合约的函数对象, 3个参数分别是：
                    Function function = new Function(
                            "transfer", //参数1：  智能合约里面的函数名称
                            //参数2： 智能合约要执行的这个函数要传入的参数列表
                            Arrays.asList(new Address(toAddress), new Uint256(new BigInteger(amount))),
                            //参数3： 智能合约要执行的这个函数的返回值的类型
                            Collections.singletonList(new TypeReference<Bool>() {
                            }));
                    String encode = FunctionEncoder.encode(function);

                    //创建未签名的交易对象, 需要传入5个参数: nonce, gasPrice, gasLimit, toAddress, encode
                    // 第一个参数nonce, 也就是钱包的历史交易次数
                    BigInteger nonce = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).send().getTransactionCount();

                    //第二个参数gasPrice, 邮费
                    BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

                    //第三个参数gasLimit, 最大邮费数量
                    BigInteger gasLimit = new BigInteger("300000");

                    //第四个参数to, 说明要把这笔金额转到哪里去
                    //因为要执行的是智能合约里面的函数,所以这里传入智能合约地址,表示由智能合约来操作这笔金额
                    //第五个参数encode,传入进过encode的函数对象,也就是智能合约里面要执行的函数
                    //生成未经过签名的ranTransaction对象
                    RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddress, encode);

                    //获取钱包根节点公私钥
                    ECKeyPair ecKeyPair = Wallet.decrypt(PASSWORD, walletFile);

                    //根据密钥对生成证书
                    Credentials credentials = Credentials.create(ecKeyPair);

                    //使用证书对交易对象进行消息摘要运算,也就是签名,并返回签名的字节数组
                    byte[] bytes = TransactionEncoder.signMessage(rawTransaction, credentials);

                    //将签名的字节数组转化成十六进制的字符串
                    String hexString = Numeric.toHexString(bytes);

                    //使用签名,进行交易,并返回本次交易的哈希值
                    String transactionHash = web3j.ethSendRawTransaction(hexString).sendAsync().get().getTransactionHash();

                    Log.d(TAG, "onSendToken:  本次Token交易的哈希值是： " + transactionHash);

                    //刷新Token余额
                    loadTokenBalance();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    //使用助记词, 生成确定性钱包
    private void createWalletByMnemonic() {
        //生成随机的128位, 也就是16字节的数组
        byte[] entropy = new byte[DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8];
        new SecureRandom().nextBytes(entropy);

        //使用生成的随机字节数组生成助记词
        try {
            List<String> mnemonics = MnemonicCode.INSTANCE.toMnemonic(entropy);
            Log.d(TAG, "createWalletByMnemonic:  生成的12个助记词是: ");
            for (String mnemonic : mnemonics) {
                Log.d(TAG, mnemonic);
            }

            //使用助记词生成种子
            byte[] seeds = MnemonicCode.INSTANCE.toEntropy(mnemonics);

            //使用种子计算出根节点密钥对
            DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey(seeds);

            //根节点派生出子节点的公私钥
            DeterministicHierarchy deterministicHierarchy = new DeterministicHierarchy(masterPrivateKey);

            //使用BIP44协议,派生成层级确定性结构的子节点的私钥
            //createParent=true, 生成父路径,  relative=false, 相对于根路径
            DeterministicKey deterministicKey = deterministicHierarchy.deriveChild(
                    BIP44_ETH_ACCOUNT_ZERO_PATH,
                    false, true,
                    ChildNumber.ZERO);

            //获取子节点的字节数组
            byte[] privKeyBytes = deterministicKey.getPrivKeyBytes();

            //根据子节点的字节数组创建密钥对
            ECKeyPair ecKeyPair = ECKeyPair.create(privKeyBytes);
            //根据密码和密钥对创建钱包,  将创建出来的钱包对象赋值给本地变量
            walletFile = Wallet.createLight(PASSWORD, ecKeyPair);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
