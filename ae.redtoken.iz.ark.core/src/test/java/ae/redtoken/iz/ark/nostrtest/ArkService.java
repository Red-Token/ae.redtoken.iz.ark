package ae.redtoken.iz.ark.nostrtest;

//import ae.redtoken.iz.ark.nostrtest.Actor;

import lombok.SneakyThrows;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.SendRequest;

import java.util.Collection;

public class ArkService extends Actor {

    public ArkService(NetworkParameters params) {
        super(params);

        kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
            System.out.println("Received coin " + coin + " to " + wallet);
        });
    }

    final static Coin FUNDING_SHARD_VALUE = Coin.valueOf(0, 1);

    @SneakyThrows
    public void createFundingShards(int num) {

        // Create funding outputs
        Transaction ftx = new Transaction();
        ftx.setVersion(2);

        for (int i = 0; i < num; i++)
            ftx.addOutput(FUNDING_SHARD_VALUE, kit.wallet().freshReceiveAddress());

        SendRequest sr = SendRequest.forTx(ftx);
        sr.feePerKb = Coin.valueOf(1000);
        kit.wallet().completeTx(sr);

        // Send it out
        kit.peerGroup().broadcastTransaction(sr.tx);
    }

//    public Collection<AppTest2.ArkVirtualTransactionNode> prepareSignatures(AppTest2.ArkTree tree) {
//
//        return tree.nodes.values().stream()
//                .filter(transaction -> !tree.roots.contains(transaction.getTxId()))
//                .map(transaction -> new AppTest2.ArkVirtualTransactionNode(
//                        transaction.serialize(),
//                        tree.getLock(transaction)))
//                .toList();
//    }
}
