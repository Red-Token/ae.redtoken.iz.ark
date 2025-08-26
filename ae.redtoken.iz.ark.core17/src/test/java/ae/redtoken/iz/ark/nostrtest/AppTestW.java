package ae.redtoken.iz.ark.nostrtest;

import org.bitcoin.tfw.ltbc.tc.LTBCMainTestCase;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Unit test for simple App.
 */
public class AppTestW extends LTBCMainTestCase {

    @Test
    public void test2() throws Exception {
        NetworkParameters params = RegTestParams.get();

        Actor arkService = new ArkService(params);
        ArkUser alice = new ArkUser(params);
        ArkUser bob = new ArkUser(params);

        ArkUser[] users = Arrays.asList(alice, bob).toArray(new ArkUser[0]);

        final double coinsToSendToArkService = 10;
        final double coinsToSendToUsers = 1;

        Assertions.assertEquals(Coin.ZERO, arkService.kit.wallet().getBalance());

        this.ltbc.sendTo(arkService.kit.wallet().freshReceiveAddress().toString(), coinsToSendToArkService);

        for (Actor user : users) {
            this.ltbc.sendTo(user.kit.wallet().freshReceiveAddress().toString(), coinsToSendToUsers);
        }

        this.ltbc.mine(16);

        // We wait for 1 second here
        Thread.sleep(1000);

        // TODO fix race condition here
        Assertions.assertEquals(Coin.valueOf((int) coinsToSendToArkService, 0), arkService.kit.wallet().getBalance());

        for (Actor user : users) {
            Assertions.assertEquals(Coin.valueOf((int) coinsToSendToUsers, 0), user.kit.wallet().getBalance());
        }

        // Create funding outputs
//        Transaction ftx = new Transaction(params);
        Transaction ftx = new Transaction();
        ftx.setVersion(2);

        for (int i = 0; i < 10; i++)
            ftx.addOutput(Coin.valueOf(0, 1), arkService.kit.wallet().freshReceiveAddress());

        {
            SendRequest sr = SendRequest.forTx(ftx);
            sr.feePerKb = Coin.valueOf(1000);
            arkService.kit.wallet().completeTx(sr);

            // Send it out
            arkService.kit.peerGroup().broadcastTransaction(sr.tx);

            // Mine
            Thread.sleep(5000);
            ltbc.mine(16);
            Thread.sleep(5000);
        }

        ECKey keyS = arkService.activeKey;

        int timeLockBlocks = 10;
        int seqLockBlocks = 100;

        final ArkScriptFactory asf = new ArkScriptFactory(seqLockBlocks, timeLockBlocks, keyS.getPubKey());

        // Multisig PSW test
        {
            byte[] lockScriptByteCode = asf.createVTXOLeafScript(alice.getActivePublicKey()).program();

//            Transaction t = new Transaction(params);
            Transaction t = new Transaction();
            t.setVersion(2);

            TransactionOutput output = t.addOutput(Coin.valueOf(0, 90), ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(lockScriptByteCode)));

            SendRequest sr = SendRequest.forTx(t);
            sr.feePerKb = Coin.valueOf(1000);
            arkService.kit.wallet().completeTx(sr);

            send(t, arkService, alice);
            // Now lets spend it and give it to alice

            Transaction t2 = new Transaction();
//            Transaction t2 = new Transaction(params);
            t2.addOutput(Coin.valueOf(80_000), alice.kit.wallet().freshReceiveAddress()); // change
            TransactionInput input = t2.addInput(output);

            //TODO: Not sure BAAHL will not eat me for this, chatGPT says it does not
            byte[] aliceSig = alice.signInputWitness(params, t2.serialize(), lockScriptByteCode, input.getIndex(), output.getValue());
            byte[] arkServiceSig = arkService.signInputWitness(params, t2.serialize(), lockScriptByteCode, input.getIndex(), output.getValue());

            TransactionWitness witness = asf.createVTXOLeafColaborativeUnlockWitness(aliceSig, arkServiceSig, lockScriptByteCode);
//            input.setWitness(witness);
            AppTest2.setWitness(input, witness);
            send(t2, arkService, alice);
        }

        System.out.println("THE END!");
    }

    private void send(Transaction tx, Actor arkService, ArkUser alice) throws InterruptedException {
        System.out.println(tx);
        arkService.kit.peerGroup().broadcastTransaction(tx);

        // Mine
        Thread.sleep(5000);
        ltbc.mine(16);
        Thread.sleep(5000);

        // Check the balance
        System.out.println(alice.kit.wallet().getBalance());
    }
}
