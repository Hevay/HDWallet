package com.vx.hdwallet.bitcoinWallet;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.vx.hdwallet.config.Constants;
import com.vx.hdwallet.Qr;
import com.vx.hdwallet.R;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.core.listeners.PeerDiscoveredEventListener;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.net.discovery.MultiplexingDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletFiles;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BitcoinWalletActivity extends AppCompatActivity {
    /*
     * 钱包分为钱包程序和钱包文件两个部分
     *
     * 层级确定性钱包（hierarchical deterministic wallets）
     * 使用BIP44协议  m/0'/0'/0'/0'
     *       '代表加固  加固后仅拿到当前节点的公私钥无法派生出子节点公私钥
     *       以保证安全. 不加固则存在安全隐患
     *           m --->  master  seed
     *           第一层 使用的是什么协议
     *           第二层  是哪个币种
     *           第三层  使用的是那个账号
     *           第四层  0 外部链接收转账     1 内部链接收找零
     *           第五层  钱包的索引编号
     * BIP44协议是一个系统可以从单一个 seed 产生一树状结构储存多组 keypairs（私钥和公钥）。
     * 好处是可以方便的备份、转移到其他相容装置（因为都只需要 seed），
     * 及分层的权限控制等。
     *
     * Simplified Payment Verification (SPV):
     *     节点无需下载所有的区块数据, 而只需要加载所有区块头数据
     *     （block header的大小为80B），
     *     即可验证这笔交易是否曾经被比特币网络认证过
     *
     *  PeerGroup  节点探索器
     *
     *  Unspent Transaction Output    交易输出中没有被花费的部分
     *  比特币钱包余额需要统计所有钱包地址对应的UTXO
     *
     * 1. 使用随机数,生成助记词
     * 2. 使用助记词生成种子(seed)
     * 3. 使用种子计算出根节点的公私钥 master
     * 4. 根节点的公私钥派生出子节点公私钥
     * */


    private static final String TAG = "BitcoinWalletActivity";

    private TextView mAddressText;
    private ImageView mQrImageView;
    private TextView mBalanceText;
    private EditText mToAddress;
    private EditText mAmount;
    private Button mSend;


    private Wallet wallet;
    private PeerGroup peerGroup;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bitcoin_wallet);
        //生成确定性公私钥
        //generateDeterministicKey();
        initView();
        //创建钱包，涉及到文件操作，并且属于耗时操作， 开启异步线程执行
        AsyncTask.execute(mLoadWalletTask);
    }

    private Runnable mLoadWalletTask = new Runnable() {
        @Override
        public void run() {
            try {
                //加载钱包，
                //1.本地没有钱包文件， 则创建钱包文件
                File file = getFileStreamPath("wallet-protobuf");
                if (!file.exists()) {
                    //创建钱包文件
                    //比特币主网， 会消耗真实比特币
                    //MainNetParams mainNetParams = MainNetParams.get();
                    //比特币测试网络, 创建钱包，
                    //创建钱包的过程就是依据BIP协议创建层级确定性钱包
                    //也就是generateDeterministicKey方法中的步骤
                    wallet = new Wallet(Constants.NETWORK_PARAMETERS);
                } else {//2.本地有钱包文件，加载钱包文件
                    //钱包文件已经存在，直接加载读取
                    //将protobuf文件转换成java对象， 反序列化，
                    // 将file转换成输入流
                    FileInputStream fileInputStream = new FileInputStream(file);
                    wallet = new WalletProtobufSerializer().readWallet(fileInputStream);
                    //清理钱包
                    wallet.cleanup();
                }
                //将钱包以文件形式保存起来（对象序列化), 并设置保存周期
                //第4个参数为null, 表示不监听钱包文件的变化事件
                WalletFiles walletFiles = wallet.autosaveToFile(file, 3 * 1000, TimeUnit.MILLISECONDS, null);
                //保存钱包文件
                walletFiles.saveNow();
            } catch (Exception e) {
                e.printStackTrace();
            }
            //更新UI界面
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateUI();
                }
            });

            //给钱包设置监听器，监听余额变化
            wallet.addCoinsReceivedEventListener(mWalletCoinsReceivedEventListener);
            //同步区块数据
            startAsyncBlockChain();
        }
    };

    private void startAsyncBlockChain() {
        try {
            File dir = getDir("blockstore", MODE_PRIVATE);
            //创建区块链文件
            File blockchainFile = new File(dir, "blockchain");

            //创建SPVBlockStore管理区块数据  spv简化的付款验证
            //即值加载区块头部数据,即可校验这笔交易是否被比特币网络验证过
            SPVBlockStore spvBlockStore = new SPVBlockStore(Constants.NETWORK_PARAMETERS, blockchainFile);
            //加载检查点,预加载一段数据
            //读取检查点文件, 转换成InputStream (输入流)
            InputStream checkpoints = getAssets().open("checkpoints.txt");
            CheckpointManager.checkpoint(Constants.NETWORK_PARAMETERS, checkpoints, spvBlockStore, wallet.getEarliestKeyCreationTime());

            //创建BlockChain对象, 包含SPVBlockStore 和 Wallet
            BlockChain blockChain = new BlockChain(Constants.NETWORK_PARAMETERS, wallet, spvBlockStore);

            //创建PeerGroup对象   节点探索器
            peerGroup = new PeerGroup(Constants.NETWORK_PARAMETERS, blockChain);
            //配置钱包
            peerGroup.addWallet(wallet);
            //设置节点探索器最大连接数
            peerGroup.setMaxConnections(8);

            //监听节点连接事件
            peerGroup.addConnectedEventListener(mPeerConnectedEventListener);
            //监听节点连接中断事件
            peerGroup.addDisconnectedEventListener(mPeerDisconnectedEventListener);
            //监听节点发现新的连接事件
            peerGroup.addDiscoveredEventListener(mPeerDiscoveredEventListener);

            //配置节点探索器
            peerGroup.addPeerDiscovery(new PeerDiscovery() {
                private final PeerDiscovery nomarPeerDiscovery = MultiplexingDiscovery
                        .forServices(Constants.NETWORK_PARAMETERS, 0);

                @Override
                public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
                    return nomarPeerDiscovery.getPeers(services, timeoutValue, timeoutUnit);
                }

                @Override
                public void shutdown() {
                    nomarPeerDiscovery.shutdown();
                }
            });

            //开始同步,探索节点并连接
            //peerGroup.start();//同步,耗时操作,线程会卡住

            peerGroup.startAsync();//异步

            //同步下载区块链数据
            peerGroup.startBlockChainDownload(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private WalletCoinsReceivedEventListener mWalletCoinsReceivedEventListener = new WalletCoinsReceivedEventListener() {
        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            updateUI();
        }
    };

    private PeerConnectedEventListener mPeerConnectedEventListener = new PeerConnectedEventListener() {
        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            //打印节点名称
            Log.d(TAG, "onPeerConnected:  是哪个节点连接上了 -->  " + peer.toString());
        }
    };

    private PeerDisconnectedEventListener mPeerDisconnectedEventListener = new PeerDisconnectedEventListener() {
        @Override
        public void onPeerDisconnected(Peer peer, int peerCount) {
            Log.d(TAG, "onPeerDisconnected:  是哪个节点的连接中断了 -->  " + peer.toString());
        }
    };

    private PeerDiscoveredEventListener mPeerDiscoveredEventListener = new PeerDiscoveredEventListener() {
        @Override
        public void onPeersDiscovered(Set<PeerAddress> peerAddresses) {
            Log.d(TAG, "onPeersDiscovered:  发现了几个新的节点  -->  " + String.valueOf(peerAddresses.size()));
        }
    };

    //确认交易并全网广播
    private void sendTransaction() {
        //获取用户输入的地址和金额
        String toAddressString = mToAddress.getText().toString();
        String amountString = mAmount.getText().toString();//以mBTC为单位
        if (TextUtils.isEmpty(toAddressString) || TextUtils.isEmpty(toAddressString.trim())) {
            Toast.makeText(this, "对不起，转账地址不能为空，请重新输入", Toast.LENGTH_SHORT).show();
        } else if (TextUtils.isEmpty(amountString) || TextUtils.isEmpty(amountString.trim())) {
            Toast.makeText(this, "对不起，转账金额不能为空，请重新输入", Toast.LENGTH_SHORT).show();
        } else {
            try {
                //将地址字符串和金额字符串转换成地址对象和比特币对象
                Address address = Address.fromBase58(Constants.NETWORK_PARAMETERS, toAddressString);
                //代码中都是以satoshi为单位
                Coin coin = MonetaryFormat.MBTC.parse(amountString); //以satoshi为单位

                //生成交易请求
                SendRequest sendRequest = SendRequest.to(address, coin);

                //使用交易请求并以线下交易的方式, 生成一个交易对象
                Transaction transaction = wallet.sendCoinsOffline(sendRequest);

                //将这笔交易在全网广播出去
                peerGroup.broadcastTransaction(transaction);
            } catch (InsufficientMoneyException e) {
                e.printStackTrace();
            }
        }
    }

    private void initView() {
        mAddressText = findViewById(R.id.address);
        mBalanceText = findViewById(R.id.balance);
        mQrImageView = findViewById(R.id.qr_code);
        mToAddress = findViewById(R.id.to_address);
        mAmount = findViewById(R.id.amount);
        mSend = findViewById(R.id.send);
        mSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTransaction();
            }
        });
    }

    //需要在主线程执行，更新UI界面
    private void updateUI() {
        //获取钱包地址
        Address address = wallet.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS);

        //使用地址生成二维码
        String s = BitcoinURI.convertToBitcoinURI(address, null, null, null);
        final Bitmap bitmap = Qr.bitmap(s);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), bitmap);
        bitmapDrawable.setFilterBitmap(false);

        //更新钱包余额
        Coin balance = wallet.getBalance(Wallet.BalanceType.ESTIMATED);
        //long value = balance.getValue(); //返回值是以satoshi为单位， 即聪
        //代码中都是以satoshi为单位
        long balanceMBTC = balance.getValue() / 10000; //转换为mBTC
        String balanceString = String.valueOf(balanceMBTC) + "mBTC";

        //将钱包地址，钱包地址的二维码， 余额分别更新到UI界面上
        mAddressText.setText("0x" + address.toString());
        mQrImageView.setImageDrawable(bitmapDrawable);
        mBalanceText.setText(balanceString);
    }

    //生成助记词到派生出子节点公私钥的过程,即头部注释第1到4步
    private void generateDeterministicKey() {
        try {
            //1. 使用随机数,生成助记词
            SecureRandom secureRandom = new SecureRandom();
            //new 一个128bit, 也就是16个字节的byte 数组
            byte[] entropy = new byte[DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8];
            //给byte数组赋给随机值
            secureRandom.nextBytes(entropy);
            //生成助记词
            List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
            for (String word : words) {
                Log.d(TAG, word);//打印12个助记词
            }

            // 2. 使用助记词生成种子(seed)
            byte[] seeds = MnemonicCode.INSTANCE.toEntropy(words);

            //3. 使用种子计算出根节点的公私钥 master
            DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey(seeds);

            //4. 根节点的公私钥派生出子节点公私钥
            DeterministicHierarchy deterministicHierarchy = new DeterministicHierarchy(masterPrivateKey);
            DeterministicKey deterministicKey = deterministicHierarchy.
                    deriveChild(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH,
                            true, true,
                            new ChildNumber(0, false));
            BigInteger privKey = deterministicKey.getPrivKey();
            Log.d(TAG, "generateDeterministicKey: " + privKey.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

