package ae.redtoken.iz.ark.nostrtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import nostr.event.Kind;
import nostr.event.impl.Filters;
import nostr.event.impl.GenericEvent;
import org.bitcoin.tfw.ltbc.tc.LTBCMainTestCase;
import org.bitcoinj.core.*;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static ae.redtoken.iz.ark.nostrtest.TestNostr.RELAYS;

/**
 * Unit test for simple App.
 */
public class AppTest2 extends LTBCMainTestCase {

    static TransactionOutput findOutput(Transaction tx, Script rs) {
        return tx.getOutputs().stream().filter(o -> Arrays.equals(o.getScriptBytes(), ScriptBuilder.createP2SHOutputScript(rs).getProgram())).findFirst().orElseThrow();
    }

    static TransactionOutput findOutputWitness(Transaction tx, byte[] rs) {
        return tx.getOutputs().stream().filter(o -> Arrays.equals(o.getScriptBytes(), ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(rs)).getProgram())).findFirst().orElseThrow();
    }


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

        {
            ///  Create the root node
            // Create the transaction
            Transaction ctx = new Transaction(params);
            ctx.setVersion(2);

            byte[][] userKeys = List.of(alice, bob, carol, david).stream().map(Actor::getActivePublicKey).toArray(byte[][]::new);

            Script rs1 = asf.createVTXONodeScript(userKeys);

            // Create the output and send in the hash of the script into that output.
            ctx.addOutput(Coin.valueOf(4, 0), ScriptBuilder.createP2WSHOutputScript(rs1));

            // Now let's complete and fund this transaction
            // Todo: this part here needs to be rewritten to work with the signing strategy
            {
                SendRequest sr = SendRequest.forTx(ctx);
                sr.feePerKb = Coin.valueOf(1000);
                arkService.kit.wallet().completeTx(sr);

                // Send it out
                arkService.kit.peerGroup().broadcastTransaction(sr.tx);

                // Mine
                Thread.sleep(5000);
                ltbc.mine(16);
                Thread.sleep(5000);
            }

            // Now we make the next node.
            {
                // On the ArkService side
                Transaction vtx1 = new Transaction(params);
                // And yes we should always  do this
                vtx1.setVersion(2);

                // Now we create the outputs
                // A + B + S | S + T=100
                Script rs1_1 = asf.createVTXONodeScript(List.of(alice, bob).stream().map(Actor::getActivePublicKey).toArray(byte[][]::new));

                // Create the output and send in the hash of the script into that output.
                vtx1.addOutput(Coin.valueOf(2, 0), ScriptBuilder.createP2WSHOutputScript(rs1_1));

                // C + D + S | S + T=100
                Script rs1_2 = asf.createVTXONodeScript(List.of(carol, david).stream().map(Actor::getActivePublicKey).toArray(byte[][]::new));

                // Create the output and send in the hash of the script into that output.
                vtx1.addOutput(Coin.valueOf(2, 0), ScriptBuilder.createP2WSHOutputScript(rs1_2));

                TransactionInput ti1 = vtx1.addInput(findOutputWitness(ctx, rs1.getProgram()));

                // Fund it
                fund(vtx1, arkService);

                // Sign it
                {
                    byte[] tx = vtx1.bitcoinSerialize();
                    byte[] program = rs1.getProgram();

                    byte[] sigABin = alice.signInputWitness(params, tx, program, ti1.getIndex(), Objects.requireNonNull(ti1.getConnectedOutput()).getValue());
                    byte[] sigBBin = bob.signInputWitness(params, tx, program, ti1.getIndex(), Objects.requireNonNull(ti1.getConnectedOutput()).getValue());
                    byte[] sigCBin = carol.signInputWitness(params, tx, program, ti1.getIndex(), Objects.requireNonNull(ti1.getConnectedOutput()).getValue());
                    byte[] sigDBin = david.signInputWitness(params, tx, program, ti1.getIndex(), Objects.requireNonNull(ti1.getConnectedOutput()).getValue());

                    byte[] sigSBin = arkService.signInputWitness(params, tx, program, ti1.getIndex(), Objects.requireNonNull(ti1.getConnectedOutput()).getValue());

                    // TODO Note we have to add the signatures in reverse order FIX THIS
                    TransactionWitness witness = ArkScriptFactory.createVTXONodeUnlockWitnessScript(new byte[][]{sigDBin, sigCBin, sigBBin, sigABin}, sigSBin, rs1.getProgram());
                    ti1.setWitness(witness);
                }

//                // Send it in
//                send(vtx1, arkService, rs1, alice);
//                fundAndSend(vtx1, arkService, rs1, alice);

                // Create the next step
                Transaction vtx1_1 = new Transaction(params);
                vtx1_1.setVersion(2);

                // Create the leafs
                // Now we create the outputs
                // A + S | A + dT=10
                byte[] rs1_1_1 = asf.createVTXOLeafScript(alice.getActivePublicKey()).getProgram();

                // Create the output and send in the hash of the script into that output.
                vtx1_1.addOutput(Coin.valueOf(1, 0), ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(rs1_1_1)));

                // B + S | B + dT=10
                byte[] rs1_1_2 = asf.createVTXOLeafScript(bob.getActivePublicKey()).getProgram();

                // Create the output and send in the hash of the script into that output.
                vtx1_1.addOutput(Coin.valueOf(1, 0), ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(rs1_1_2)));

                // Connect the input
                TransactionInput ti_1_1 = vtx1_1.addInput(findOutputWitness(vtx1, rs1_1.getProgram()));

                // Fund the transaction
                fund(vtx1_1, arkService);

                // Sign the input by everybody
                {
                    byte[] tx = vtx1_1.bitcoinSerialize();
                    byte[] program = rs1_1.getProgram();

                    byte[] sigABin = alice.signInputWitness(params, tx, program, ti_1_1.getIndex(), Objects.requireNonNull(ti_1_1.getConnectedOutput()).getValue());
                    byte[] sigBBin = bob.signInputWitness(params, tx, program, ti_1_1.getIndex(), Objects.requireNonNull(ti_1_1.getConnectedOutput()).getValue());
                    byte[] sigSBin = arkService.signInputWitness(params, tx, program, ti_1_1.getIndex(), Objects.requireNonNull(ti_1_1.getConnectedOutput()).getValue());

                    TransactionWitness witness = ArkScriptFactory.createVTXONodeUnlockWitnessScript(new byte[][]{sigBBin, sigABin}, sigSBin, rs1_1.getProgram());
                    ti_1_1.setWitness(witness);
                }

                // Create a Unilateral exit
                // Agreed exit for A
                Transaction vtx1_1_1 = new Transaction(params);
                vtx1_1_1.setVersion(2);

                // Create the output and send in the hash of the script into that output.
                vtx1_1_1.addOutput(Coin.valueOf(0, 66), alice.kit.wallet().freshReceiveAddress());

                // Connect the input
                TransactionInput ti_1_1_1 = vtx1_1_1.addInput(findOutputWitness(vtx1_1, rs1_1_1));

                fund(vtx1_1_1, arkService);
                // Sign the input by everybody
                {
                    byte[] sigABin = alice.signInputWitness(params, vtx1_1_1.bitcoinSerialize(), rs1_1_1, ti_1_1_1.getIndex(), Objects.requireNonNull(ti_1_1_1.getConnectedOutput()).getValue());
                    byte[] sigSBin = arkService.signInputWitness(params, vtx1_1_1.bitcoinSerialize(), rs1_1_1, ti_1_1_1.getIndex(), Objects.requireNonNull(ti_1_1_1.getConnectedOutput()).getValue());

                    ti_1_1_1.setWitness(asf.createVTXOLeafColaborativeUnlockWitness(sigABin, sigSBin, rs1_1_1));
                }

                // Let's make an on-chain charity output
//                fundAndSend(vtx1, arkService, rs1, alice);
//                fundAndSend(vtx1_1, arkService, rs1_1, alice);

                send(vtx1, arkService, rs1, alice);
                send(vtx1_1, arkService, rs1_1, alice);
                send(vtx1_1_1, arkService, null, alice);
//
//                // Send it in
//                {
////                    SendRequest sr = SendRequest.forTx(vtx1_1_1);
////                    sr.feePerKb = Coin.valueOf(1000);
////                    arkService.kit.wallet().completeTx(sr);
//                    vtx1_1_1.getInput(0).getScriptSig().correctlySpends(vtx1_1_1, 0, ScriptBuilder.createP2SHOutputScript(rs1_1_1), Script.ALL_VERIFY_FLAGS);
//
//                    // Send it out
//                    arkService.kit.peerGroup().broadcastTransaction(vtx1_1_1);
//                    System.out.println(vtx1_1_1);
//
//                    // Mine
//                    Thread.sleep(5000);
//                    ltbc.mine(16);
//                    Thread.sleep(5000);
//                    System.out.println(alice.kit.wallet().getBalance());
//                }

                System.out.println("HLLSLSSL");
                Assertions.assertEquals(166000000, alice.kit.wallet().getBalance().value);
            }
        }

//        Script redeemScript = ArkScriptFactory.createVTXOLeaf(alice.activeKey, keyS, timeLockBlocks);
//        Script p2shScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
//
//        Transaction txOut = new Transaction(params);
//        TransactionOutput output = txOut.addOutput(Coin.valueOf(1, 0), p2shScript);
//
//        SendRequest sr = SendRequest.forTx(txOut);
//        sr.feePerKb = Coin.valueOf(1000);
//        arkService.kit.wallet().completeTx(sr);
//
//        arkService.kit.wallet().addWatchedScripts(List.of(p2shScript));
//        alice.kit.wallet().addWatchedScripts(List.of(p2shScript));
//
//        arkService.kit.peerGroup().broadcastTransaction(sr.tx);
//
//        Thread.sleep(5000);
//        ltbc.mine(16);
//        Thread.sleep(5000);
//
//        TransactionOutput to = arkService.kit.wallet().getWatchedOutputs(false).stream()
//                .filter(transactionOutput -> transactionOutput.getScriptPubKey().equals(p2shScript))
//                .findFirst()
//                .orElseThrow();
//
////        arkService.kit.wallet().getWatchedScripts().forEach(script -> {
////            System.out.println(script.equals(p2shScript));
////        });
//
////        TransactionOutput to = arkService.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getScriptPubKey().equals(p2shScript)).findFirst().orElseThrow();
//
//        System.out.println(alice.kit.wallet().getBalance());
//
//        Transaction tx = new Transaction(params);
//
//        // This enables lockTime ie blockHeight lock
////        tx.setLockTime(10);
//
//        // And yes we should always  do this
//        tx.setVersion(2);
//        tx.addInput(to.getOutPointFor().getConnectedOutput()); // OutPoint from the UTXO (txHash + outputIndex)
//
//        // This also enables lockTime ie relative timeLock
////        tx.getInput(0).setSequenceNumber(10);
//
//        Address recipientAddress = arkService.kit.wallet().freshReceiveAddress();
//        tx.addOutput(Coin.valueOf(50_000), recipientAddress);
//        tx.addOutput(Coin.valueOf(99_900_666), alice.kit.wallet().freshReceiveAddress());
//
//        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
//        TransactionSignature sigS = new TransactionSignature(keyS.sign(sighash), Transaction.SigHash.ALL, false);
//
//        byte[] sigABin = alice.sign(sighash.getBytes());
//        byte[] sigSBin = sigS.encodeToBitcoin();
//
////        Script inputScript = ArkScriptFactory.createVTXOUnilateralUnlock(sigABin, redeemScript);
////        Script inputScript = ArkScriptFactory.createVTXOUnlock(sigA, sigS, redeemScript);
//        Script inputScript = ArkScriptFactory.createVTXOLeafUnlockScript(sigABin, sigSBin, redeemScript);
//        tx.getInput(0).setScriptSig(inputScript);
//
//        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
//        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
//        arkService.kit.peerGroup().broadcastTransaction(tx);
//        // Broadcast it
//
//        System.out.println(tx);
//
//        ltbc.mine(6);
//        Thread.sleep(5000);
//
//        System.out.println(alice.kit.wallet().getBalance());
//
//        Assertions.assertEquals(199900666, alice.kit.wallet().getBalance().value);

//        var aliceIdentity = Identity.generateRandomIdentity();
//        var eveIdentity = Identity.generateRandomIdentity();

        /**
         *  Step 1: Eve creates a quotation
         */

        ObjectMapper om = new ObjectMapper();
        TestNostr.ArkQuotationContent aqc = new TestNostr.ArkQuotationContent();

        aqc.amount = 30000;
        aqc.pubkey = "WHATEVER";
        aqc.arks.includeOnly = true;
        aqc.arks.include = new String[]{"myarc"};
        aqc.offer.setCurrencyCode(Currency.getInstance("USD").getCurrencyCode());
        aqc.offer.items = new TestNostr.ArkOfferItems[]{
                new TestNostr.ArkOfferItems("Pepperoni", 1, 3.0)
        };
        aqc.offer.vat = "5%";

        String offer = om.writeValueAsString(aqc);
        System.out.println(offer);

        TestNostr.NIP0666<TestNostr.NIP0666ArkQuotationEvent> nip0666Stack = new TestNostr.NIP0666<>();
        nip0666Stack.setSender(eve.identity);
        nip0666Stack.setRelays(RELAYS);
        TestNostr.NIP0666ArkQuotationEventFactory xy = new TestNostr.NIP0666ArkQuotationEventFactory(eve.identity, offer);
        nip0666Stack.setEvent(xy.create());
        nip0666Stack.signAndSend();

        /**
         *  Step 2: Alice scans the bitcoin URL and fetches the offer
         */

        String id = nip0666Stack.getEvent().getId();

        TestNostr.NIP0666<TestNostr.NIP0666ArkQuotationEvent> aliceNip0666Stack = new TestNostr.NIP0666<>();
        aliceNip0666Stack.setRelays(RELAYS);
        aliceNip0666Stack.setSender(alice.identity);
        GenericEvent ge = new GenericEvent();
        ge.setId(id);
        Filters filters2 = Filters.builder().events(List.of(ge)).kinds(List.of(Kind.ARK_QUOTATION)).build();
        String subId2 = "sub_" + alice.identity.getPublicKey();

        BlockingQueue<GenericEvent> queue = new ArrayBlockingQueue<>(1);

        EventCustomHandler2.handlers.put(subId2, (event, message, relay) -> {
            queue.add((GenericEvent) event);
        });

        aliceNip0666Stack.send(filters2, subId2);

        GenericEvent take = queue.take();
        System.out.println(take);


        System.out.println("THE END!");

        /*
         *  Scenario 1
         *
         *  Alice, Bob, Carol, Dave have 1 BTC each in an ARK managed by arkService.
         *
         *  Alice then sends 0.5 BTC to Eve, and Freddy joins the group with 1 BTC. And finally Clare decide she wants to leave with 0.3 BTC.
         *
         *  A new round is created by arkService.
         *
         *  First arkService proposes a new foundation tree.
         *
         *  All parties are then proposes to sign, and fund the nodes in the tree from there current VTXO:s exit transaction.
         *
         *  The makes the root of the round tree valid.
         *
         *  In the second stage tha participants are asked to sign the root nodes of the current round.
         *
         *  Once this is done arkService deposits the tree transition node to the blockchain.
         *
         *  Noster based communication
         *
         *  Eve sends a payment request using a bitcoin uri
         *
         *  bitcoin://#bitcoinaddress#?amount=0.01&lightning=#lightninginvoice#&ark=#npub#
         *
         *  payment request  {
         *      amount: #######
         *      arks: {
         *          include: [ ark1_npub, ark2_npub ]
         *          exclude: [ ark2_npub, ark3_npub ]
         *          will_reject_not_included: true
         *      }
         *      recite {
         *          #description of goods your are paying for#
         *      }
         *  }
         *
         *  payment_offer {
         *      amount: #######
         *      ark: ark2_npub
         *  }
         *
         *  payment_accept {
         *      transaction_key: XXXXXXX
         *  }
         *
         *  payment_recit {
         *      vtxo: [ #transactions# ]
         *  }
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         */


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

    private void send(Transaction tx, Actor arkService, Script rs, ArkUser alice) throws InterruptedException {
//        fund(tx, arkService);

        // Verify that the input is correct
//        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, ScriptBuilder.createP2SHOutputScript(rs), Script.ALL_VERIFY_FLAGS);

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
