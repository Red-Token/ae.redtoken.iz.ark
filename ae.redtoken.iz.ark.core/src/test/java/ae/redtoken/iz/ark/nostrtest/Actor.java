package ae.redtoken.iz.ark.nostrtest;

import nostr.id.Identity;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.AbstractBitcoinNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

class Actor {

    WalletAppKit kit;
    Identity identity;
    ECKey activeKey;

    Actor(AbstractBitcoinNetParams params) {
//            RegTestParams params = RegTestParams.get();

        try {
            kit = new WalletAppKit(params, Files
                    .createTempDirectory("wallet").toFile(), "dat");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        kit.connectToLocalHost();
        kit.startAsync().awaitRunning();

//            kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
//                System.out.println("Received coin " + coin + " to " + wallet);
//            });

        this.identity = Identity.generateRandomIdentity();
        this.activeKey = this.kit.wallet().freshReceiveKey();
    }


    byte[] signInput(NetworkParameters parameters, byte[] transaction, byte[] program) {
        Transaction tx = new Transaction(parameters, transaction);
        return signStack(tx.hashForSignature(0, program, Transaction.SigHash.ALL, true).getBytes());
    }

    byte[] signStack(byte[] hash) {
        return new TransactionSignature(activeKey.sign(Sha256Hash.wrap(hash)), Transaction.SigHash.ALL, true).encodeToBitcoin();
    }

    void signSpendingInput(TransactionInput input) {
        ECKey key = this.kit.wallet().findKeyFromPubKeyHash(Objects.requireNonNull(input.getConnectedOutput()).getScriptPubKey().getPubKeyHash(), Script.ScriptType.P2PKH);

        // 2. The P2PKH scriptPubKey (the one you're spending from)
        Script scriptPubKey = ScriptBuilder.createP2PKHOutputScript(Objects.requireNonNull(key));

        // 3. Sign the input
        int inputIndex = input.getIndex();  // adjust if needed
        Transaction.SigHash sigHash = Transaction.SigHash.ALL;
        boolean anyoneCanPay = true;

        // 4. Create the hash for signature
        Sha256Hash sigHashBytes = Objects.requireNonNull(input.getParentTransaction()).hashForSignature(inputIndex, scriptPubKey, sigHash, anyoneCanPay);

        // 5. Create the ECDSA signature
        ECKey.ECDSASignature signature = key.sign(sigHashBytes);
        TransactionSignature txSig = new TransactionSignature(signature, sigHash, anyoneCanPay);

        // 6. Create scriptSig (the unlocking script)
        Script inputScript = ScriptBuilder.createInputScript(txSig, key);

        input.setScriptSig(inputScript);
    }


    public byte[] getActivePublicKey() {
        return activeKey.getPubKey();
    }

    public byte[] signInputWitness(NetworkParameters params, byte[] transactionBytes, byte[] lockScriptByteCode, int index, Coin value) {
        Transaction tx = new Transaction(params, transactionBytes);

        Sha256Hash sigHash = tx.hashForWitnessSignature(
                index,
                lockScriptByteCode,
                value,
                Transaction.SigHash.ALL,
                true
        );

        return signStack(sigHash.getBytes());
    }

    public Map<Sha256Hash,byte[]> signStack(NetworkParameters params, AppTest2.ArkVirtualTransactionStack vtxs) {

        Map<Sha256Hash, Transaction> treeMap = new HashMap<>();
        Transaction root = new Transaction(params, vtxs.root);
        treeMap.put(root.getTxId(), root);
        Map<Sha256Hash, byte[]> sigMap = new HashMap<>();

//        // Connect the input
//        TransactionInput ti1 = vtx1.addInput(findOutputWitness(ctx, rs1));
//
//        // Sign it
//        {
//            byte[] tx = vtx1.bitcoinSerialize();
//            byte[] program = rs1;
//
//            byte[] sigABin = alice.signInputWitness(params, tx, program, ti1.getIndex(), Objects.requireNonNull(ti1.getConnectedOutput()).getValue());
//            byte[] sigBBin = bob.signInputWitness(params, tx, program, ti1.getIndex(), Objects.requireNonNull(ti1.getConnectedOutput()).getValue());

        vtxs.nodes.stream().forEachOrdered(node -> {
            Transaction t = new Transaction(params, node.transaction);
            treeMap.put(t.getTxId(), t);

            t.getInputs().stream().filter(ti -> treeMap.containsKey(ti.getOutpoint().getHash())).forEachOrdered(
                    ti ->  {
                        TransactionOutput to = AppTest2.findOutputWitness(treeMap.get(ti.getOutpoint().getHash()), node.program);
                        sigMap.put(Sha256Hash.of(node.program), signInputWitness(params, node.transaction, node.program, ti.getIndex(), to.getValue()));
                    }
            );

                    System.out.println(t);
//
//
//            byte[] sigABin = signInputWitness(params, node.transaction, node.program, node.index, Coin.valueOf(node.value));
        });

        return sigMap;
    }
}
