package ae.redtoken.iz.ark.minitest;

import lombok.SneakyThrows;
import org.bitcoin.tfw.ltbc.tc.LTBCMainTestCase;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.wallet.SendRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MiniTest extends LTBCMainTestCase {

    @SneakyThrows
    @Test
    void myMiniTest() {

        System.out.println("myMiniTest");

        RegTestParams regTestParams = RegTestParams.get();

        Actor a = new Actor(regTestParams);
        Actor b = new Actor(regTestParams);

        double coinsToSendToUsers = 10.0;
        this.ltbc.sendTo(a.kit.wallet().freshReceiveAddress().toString(), coinsToSendToUsers);
        this.ltbc.mine(6);

        Thread.sleep(1000);

        // TODO fix race condition here
        Assertions.assertEquals(Coin.valueOf((int) coinsToSendToUsers, 0), a.kit.wallet().getBalance());
        Assertions.assertEquals(Coin.valueOf(0, 0), b.kit.wallet().getBalance());

        Thread.sleep(5000);
        ltbc.mine(16);
        Thread.sleep(5000);


        // Let's create a transaction with a nice withness scrip to send to b
        Transaction tx = new Transaction(regTestParams);
        tx.setVersion(2);

        // Add and input
        {
            TransactionOutput output = a.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getValue().isGreaterThan(Coin.valueOf(1, 0)) && transactionOutput.isAvailableForSpending()).findFirst().orElseThrow();
            TransactionInput ti = tx.addInput(output);
            output.markAsSpent(ti);
        }

        // Add output
        tx.addOutput(Coin.valueOf(1, 0), b.kit.wallet().freshReceiveAddress());

        // Add change

        // Sign

        // Send
        SendRequest sr = SendRequest.forTx(tx);
        sr.feePerKb = Coin.valueOf(1000);
        a.kit.wallet().completeTx(sr);

        System.out.println(sr.tx);

        // Send it out
        a.kit.peerGroup().broadcastTransaction(sr.tx);

//        Thread.sleep(1000);
//        this.ltbc.mine(6);
//        Thread.sleep(1000);

        Thread.sleep(5000);
        ltbc.mine(16);
        Thread.sleep(5000);


        System.out.println(a.kit.wallet().getBalance());
        System.out.println(b.kit.wallet().getBalance());
    }
}
