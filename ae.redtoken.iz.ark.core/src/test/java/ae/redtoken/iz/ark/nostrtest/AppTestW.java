package ae.redtoken.iz.ark.nostrtest;

import nostr.id.Identity;
import org.bitcoin.tfw.ltbc.tc.LTBCMainTestCase;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.AbstractBitcoinNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Unit test for simple App.
 */
public class AppTestW extends LTBCMainTestCase {

//    static class Actor {
//
//        WalletAppKit kit;
//        Identity identity;
//        ECKey activeKey;
//
//        Actor(AbstractBitcoinNetParams params) {
////            RegTestParams params = RegTestParams.get();
//
//            try {
//                kit = new WalletAppKit(params, Files
//                        .createTempDirectory("wallet").toFile(), "dat");
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//            kit.connectToLocalHost();
//            kit.startAsync().awaitRunning();
//
////            kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
////                System.out.println("Received coin " + coin + " to " + wallet);
////            });
//
//            this.identity = Identity.generateRandomIdentity();
//            this.activeKey = this.kit.wallet().freshReceiveKey();
//        }
//
//
//        byte[] signInputW(NetworkParameters parameters, byte[] transaction, int index, Coin value, byte[] program) {
//            Transaction tx = new Transaction(parameters, transaction);
//
//            Sha256Hash sigHash = tx.hashForWitnessSignature(
//                    index,
//                    program,
//                    value,
//                    Transaction.SigHash.ALL,
//                    true
//            );
//
//            TransactionSignature aliceSig = new TransactionSignature(activeKey.sign(sigHash), Transaction.SigHash.ALL, true);
//            return aliceSig.encodeToBitcoin();
//        }
//
//        byte[] signInput(NetworkParameters parameters, byte[] transaction, byte[] program) {
//            Transaction tx = new Transaction(parameters, transaction);
//            return sign(tx.hashForSignature(0, program, Transaction.SigHash.ALL, true).getBytes());
//        }
//
//
//        byte[] sign(byte[] hash) {
//            return new TransactionSignature(activeKey.sign(Sha256Hash.wrap(hash)), Transaction.SigHash.ALL, true).encodeToBitcoin();
//        }
//
//        void signSpendingInput(TransactionInput input) {
//            ECKey key = this.kit.wallet().findKeyFromPubKeyHash(Objects.requireNonNull(input.getConnectedOutput()).getScriptPubKey().getPubKeyHash(), Script.ScriptType.P2PKH);
//
//            // 2. The P2PKH scriptPubKey (the one you're spending from)
//            Script scriptPubKey = ScriptBuilder.createP2PKHOutputScript(Objects.requireNonNull(key));
//
//            // 3. Sign the input
//            int inputIndex = input.getIndex();  // adjust if needed
//            Transaction.SigHash sigHash = Transaction.SigHash.ALL;
//            boolean anyoneCanPay = true;
//
//            // 4. Create the hash for signature
//            Sha256Hash sigHashBytes = Objects.requireNonNull(input.getParentTransaction()).hashForSignature(inputIndex, scriptPubKey, sigHash, anyoneCanPay);
//
//            // 5. Create the ECDSA signature
//            ECKey.ECDSASignature signature = key.sign(sigHashBytes);
//            TransactionSignature txSig = new TransactionSignature(signature, sigHash, anyoneCanPay);
//
//            // 6. Create scriptSig (the unlocking script)
//            Script inputScript = ScriptBuilder.createInputScript(txSig, key);
//
//            input.setScriptSig(inputScript);
//        }
//
//
//        public byte[] getActivePublicKey() {
//            return activeKey.getPubKey();
//        }
//    }
//
//    static class ArkService extends Actor {
//
//        ArkService(AbstractBitcoinNetParams params) {
//            super(params);
//
//            kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
//                System.out.println("Received coin " + coin + " to " + wallet);
//            });
//
//        }
//    }
//
//    static class ArkUser extends Actor {
//
//
//        ArkUser(AbstractBitcoinNetParams params) {
//            super(params);
//        }
//    }
//
//    static TransactionOutput findOutput(Transaction tx, Script rs) {
//        return tx.getOutputs().stream().filter(o -> Arrays.equals(o.getScriptBytes(), ScriptBuilder.createP2SHOutputScript(rs).getProgram())).findFirst().orElseThrow();
//    }
//
//    static TransactionOutput findOutputW(Transaction tx, Script rs) {
//        return tx.getOutputs().stream().filter(o -> Arrays.equals(o.getScriptBytes(), ScriptBuilder.createP2WSHOutputScript(rs).getProgram())).findFirst().orElseThrow();
//    }


    @Test
    public void test2() throws Exception {
        RegTestParams params = RegTestParams.get();

        Actor arkService = new ArkService(params);
        ArkUser alice = new ArkUser(params);
        ArkUser bob = new ArkUser(params);
        ArkUser carol = new ArkUser(params);
        ArkUser david = new ArkUser(params);
        ArkUser eve = new ArkUser(params);
        ArkUser freddy = new ArkUser(params);

        ArkUser[] users = Arrays.asList(alice, bob, carol, david, eve, freddy).toArray(new ArkUser[0]);

//        Wallet wallet = arkService.kit.wallet();

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
        Transaction ftx = new Transaction(params);
        ftx.setVersion(2);

        for (int i = 0; i < 10; i++)
            ftx.addOutput(Coin.valueOf(0, 1), arkService.kit.wallet().freshReceiveAddress());

        {
            SendRequest sr = SendRequest.forTx(ftx);
            sr.feePerKb = Coin.valueOf(1000);
            arkService.kit.wallet().completeTx(sr);

            // Send it out
            arkService.kit.peerGroup().broadcastTransaction(sr.tx);

//                arkService.kit.wallet().addWatchedScripts(List.of(p2shScript89));
//                alice.kit.wallet().addWatchedScripts(List.of(p2shScript89));

            // Mine
            Thread.sleep(5000);
            ltbc.mine(16);
            Thread.sleep(5000);
        }


        // Create the root node


        // Generate keys
//        ECKey keyA = alice.activeKey;
//        ECKey keyA = alice.kit.wallet().freshReceiveKey();
//        ECKey keyB = bob.activeKey;
//        ECKey keyC = carol.activeKey;
//        ECKey keyD = david.activeKey;
        ECKey keyE = eve.activeKey;
        ECKey keyF = freddy.activeKey;
        ECKey keyS = arkService.activeKey;

        int timeLockBlocks = 10;
        int seqLockBlocks = 100;

        final ArkScriptFactory asf = new ArkScriptFactory(seqLockBlocks, timeLockBlocks, keyS.getPubKey());

        // Multisig PSW test
        {

//            ECKey keyA = new ECKey();
//            ECKey keyB = new ECKey();

//            Script witnessScript = ScriptBuilder.createMultiSigOutputScript(2, Arrays.asList(keyA, keyB));
//            Script lockScript = asf.createVTXOLeafScript(alice.getActivePublicKey());
            byte[] lockScriptByteCode = asf.createVTXOLeafScript(alice.getActivePublicKey()).getProgram();

            Transaction t = new Transaction(params);
            t.setVersion(2);

            TransactionOutput output = t.addOutput(Coin.valueOf(0, 90), ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(lockScriptByteCode)));

            SendRequest sr = SendRequest.forTx(t);
            sr.feePerKb = Coin.valueOf(1000);
            arkService.kit.wallet().completeTx(sr);

            send(t, arkService, null, alice);
            // Now lets spend it and give it to alice

            Transaction t2 = new Transaction(params);
            t2.addOutput(Coin.valueOf(80_000), alice.kit.wallet().freshReceiveAddress()); // change
            TransactionInput input = t2.addInput(output);

            //TODO: Not sure BAAHL will not eat me for this, chatGPT says it does not
            byte[] aliceSig = alice.signInputWitness(params, t2.bitcoinSerialize(), lockScriptByteCode, input.getIndex(), output.getValue());
            byte[] arkServiceSig = arkService.signInputWitness(params, t2.bitcoinSerialize(), lockScriptByteCode, input.getIndex(), output.getValue());

            TransactionWitness witness = asf.createVTXOLeafColaborativeUnlockWitness(aliceSig, arkServiceSig, lockScriptByteCode);
            input.setWitness(witness);
            send(t2, arkService, null, alice);
        }

        System.out.println("THE END!");
    }

    private void fund(Transaction tx, Actor arkService) throws InterruptedException {
        System.out.println("To be spent: " + arkService.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getValue().equals(Coin.valueOf(0, 1))).count());

        // Add the funding input, post transaction signature
        TransactionOutput output = arkService.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getValue().equals(Coin.valueOf(0, 1)) && transactionOutput.isAvailableForSpending()).findFirst().orElseThrow();
        TransactionInput fti1 = tx.addInput(output);
        output.markAsSpent(fti1);

        arkService.signSpendingInput(fti1);
//
//        ECKey key = arkService.kit.wallet().findKeyFromPubKeyHash(output.getScriptPubKey().getPubKeyHash(), Script.ScriptType.P2PKH);
//
//        // 2. The P2PKH scriptPubKey (the one you're spending from)
//        Script scriptPubKey = ScriptBuilder.createP2PKHOutputScript(key);
//
//        // 3. Sign the input
//        int inputIndex = fti1.getIndex();  // adjust if needed
//        Transaction.SigHash sigHash = Transaction.SigHash.ALL;
//        boolean anyoneCanPay = true;
//
//        // 4. Create the hash for signature
//        Sha256Hash sigHashBytes = tx.hashForSignature(inputIndex, scriptPubKey, sigHash, anyoneCanPay);
//
//        // 5. Create the ECDSA signature
//        ECKey.ECDSASignature signature = key.sign(sigHashBytes);
//        TransactionSignature txSig = new TransactionSignature(signature, sigHash, anyoneCanPay);
//
//        // 6. Create scriptSig (the unlocking script)
//        Script inputScript = ScriptBuilder.createInputScript(txSig, key);
//
//        fti1.setScriptSig(inputScript);

        // Sign the funding input
//        SendRequest sr = SendRequest.forTx(tx);
//        sr.ensureMinRequiredFee = false;
//        arkService.kit.wallet().signTransaction(sr);
    }

    private void verify(Transaction tx, Actor arkService, Script rs, ArkUser alice) throws InterruptedException {
        // Verify that the input is correct
        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, ScriptBuilder.createP2SHOutputScript(rs), Script.ALL_VERIFY_FLAGS);
    }

    private void verifyW(Transaction tx, Actor arkService, Script rs, ArkUser alice) throws InterruptedException {
        // Verify that the input is correct
        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, ScriptBuilder.createP2WSHOutputScript(rs), Script.ALL_VERIFY_FLAGS);
    }


    private void send(Transaction tx, Actor arkService, Script rs, ArkUser alice) throws InterruptedException {
//        fund(tx, arkService);


        // Send it out
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
