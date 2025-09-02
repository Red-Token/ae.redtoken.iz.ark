package ae.redtoken.iz.ark.nostrtest;

import org.bitcoin.tfw.ltbc.tc.LTBCMainTestCase;
import org.bitcoinj.base.*;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static ae.redtoken.iz.ark.nostrtest.AppTest2.setWitness;

/**
 * Unit test for simple App.
 */
public class TestSignatures extends LTBCMainTestCase {

    @Test
    public void testP2WPKH() throws Exception {
        NetworkParameters params = RegTestParams.get();

        ArkService arkService = new ArkService(params);
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

        arkService.createFundingShards(10);

        // mine
        ltbc.mine(1);
        Thread.sleep(1000);

        ECKey keyS = arkService.activeKey;

        int timeLockBlocks = 10;
        int seqLockBlocks = 100;

        final ArkScriptFactory asf = new ArkScriptFactory(seqLockBlocks, timeLockBlocks, keyS.getPubKey());

        // Multisig PSW test
        // Create a lockScript
//        byte[] lockScriptByteCode = asf.createVTXOLeafScript(alice.getActivePublicKey()).program();

//        Script lockScript = ScriptBuilder.createP2WPKHOutputScript(alice.activeKey);

        Address address = alice.activeKey.toAddress(ScriptType.P2WPKH, params.network());

        Transaction leafTransaction = new Transaction();
        leafTransaction.setVersion(2);

        Coin value = Coin.valueOf(0, 90);

//        TransactionOutput output = leafTransaction.addOutput(value, ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(lockScriptByteCode)));
        Script lockScript = ScriptBuilder.createP2WPKHOutputScript(alice.activeKey);
        TransactionOutput output = leafTransaction.addOutput(value, lockScript);
//        TransactionOutput output = leafTransaction.addOutput(value, address);

        // Fund our leaf from the ArkService, what a kind service
        SendRequest sr = SendRequest.forTx(leafTransaction);
        sr.feePerKb = Coin.valueOf(1000);
        arkService.kit.wallet().completeTx(sr);

        send(leafTransaction, arkService, alice);
        // Now lets spend it and give it to alice

        // Redeem the transaction for Alice
        Transaction t2 = new Transaction();
        t2.addOutput(Coin.valueOf(80_000), alice.kit.wallet().freshReceiveAddress()); // change

        TransactionInput input = t2.addInput(output);

//        //TODO: Not sure BAAHL will not eat me for this, chatGPT says it does not
//        byte[] aliceSig = alice.signInputWitness(t2.transaction(), lockScript.program(), input.getIndex(), output.getValue());
//        byte[] arkServiceSig = arkService.signInputWitness(t2.transaction(), lockScriptByteCode, input.getIndex(), output.getValue());

        // To unlock the transaction we should use this, why? Don't Know!
        Script witnessScript = ScriptBuilder.createP2PKHOutputScript(alice.activeKey);

        TransactionSignature sig = t2.calculateWitnessSignature(
                input.getIndex(),
                alice.activeKey,
                witnessScript,
                output.getValue(),
                Transaction.SigHash.ALL,
                true);


//        TransactionWitness witness = asf.createVTXOLeafColaborativeUnlockWitness(aliceSig, arkServiceSig, lockScriptByteCode);
        TransactionWitness witness = TransactionWitness.redeemP2WPKH(sig, alice.activeKey);
        setWitness(input, witness);

        send(t2, arkService, alice);

        Assertions.assertEquals(100080000, alice.kit.wallet().getBalance().value);

        System.out.println("THE END!");
    }


    @Test
    public void testLeaf() throws Exception {
        NetworkParameters params = RegTestParams.get();

        ArkService arkService = new ArkService(params);
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

        arkService.createFundingShards(10);

        // mine
        ltbc.mine(1);
        Thread.sleep(5000);

        ECKey keyS = arkService.activeKey;

        int timeLockBlocks = 10;
        int seqLockBlocks = 100;

        final ArkScriptFactory asf = new ArkScriptFactory(seqLockBlocks, timeLockBlocks, keyS.getPubKey());

        // Multisig PSW test
        // Create a lockScript
        byte[] lockScriptByteCode = asf.createVTXOLeafScript(alice.getActivePublicKey()).program();

        Transaction leafTransaction = new Transaction();
        leafTransaction.setVersion(2);

        Coin value = Coin.valueOf(0, 90);

        TransactionOutput output = leafTransaction.addOutput(value, ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(lockScriptByteCode)));

        // Fund our leaf from the ArkService, what a kind service
        SendRequest sr = SendRequest.forTx(leafTransaction);
        sr.feePerKb = Coin.valueOf(1000);
        arkService.kit.wallet().completeTx(sr);

        send(leafTransaction, arkService, alice);
        // Now lets spend it and give it to alice

        // Redeem the transaction for Alice
        Transaction t2 = new Transaction();
        t2.addOutput(Coin.valueOf(80_000), alice.kit.wallet().freshReceiveAddress()); // change
        TransactionInput input = t2.addInput(output);

        //TODO: Not sure BAAHL will not eat me for this, chatGPT says it does not
        byte[] aliceSig = alice.signInputWitness(t2.serialize(), lockScriptByteCode, input.getIndex(), output.getValue());
        byte[] arkServiceSig = arkService.signInputWitness(t2.serialize(), lockScriptByteCode, input.getIndex(), output.getValue());

        TransactionWitness witness = asf.createVTXOLeafColaborativeUnlockWitness(aliceSig, arkServiceSig, lockScriptByteCode);
        setWitness(input, witness);

        send(t2, arkService, alice);

        Assertions.assertEquals(100080000, alice.kit.wallet().getBalance().value);

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
