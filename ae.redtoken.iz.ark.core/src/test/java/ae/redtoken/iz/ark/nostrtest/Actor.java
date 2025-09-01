package ae.redtoken.iz.ark.nostrtest;

import lombok.SneakyThrows;
import nostr.id.Identity;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Actor {

    WalletAppKit kit;
    Identity identity;
    ECKey activeKey;

    AppTest2.ArkTree tree;

    Actor(NetworkParameters params) {
//            RegTestParams params = RegTestParams.get();

        try {
            kit = new WalletAppKit(params, Files
                    .createTempDirectory("wallet").toFile(), "dat");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        kit.connectToLocalHost();
        kit.startAsync().awaitRunning();
        kit.peerGroup().setBloomFilteringEnabled(false); // receive all transactions


//            kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
//                System.out.println("Received coin " + coin + " to " + wallet);
//            });

        this.identity = Identity.generateRandomIdentity();
        this.activeKey = this.kit.wallet().freshReceiveKey();

        kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
            System.out.println("Received coin " + coin + " to " + wallet);
        });

    }


    byte[] signInput(NetworkParameters parameters, byte[] transaction, byte[] program) {
//        Transaction tx = new Transaction(parameters, transaction);
        Transaction tx = Transaction.read(ByteBuffer.wrap(transaction));
        return signHash(tx.hashForSignature(0, program, Transaction.SigHash.ALL, true).getBytes());
    }

    byte[] signHash(byte[] hash) {
        return new TransactionSignature(activeKey.sign(Sha256Hash.wrap(hash)), Transaction.SigHash.ALL, true).encodeToBitcoin();
    }

    TransactionInput signSpendingInput(TransactionInput input) {
        ECKey key = this.kit.wallet().findKeyFromPubKeyHash(Objects.requireNonNull(input.getConnectedOutput()).getScriptPubKey().getPubKeyHash(), ScriptType.P2PKH);

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

//        input.setScriptSig(inputScript);
        return input.withScriptSig(inputScript);
    }


    public byte[] getActivePublicKey() {
        return activeKey.getPubKey();
    }

    public byte[] signInputWitness(byte[] transactionBytes, byte[] lockScriptByteCode, int index, Coin value) {
//        Transaction tx = new Transaction(params, transactionBytes);
        Transaction tx = Transaction.read(ByteBuffer.wrap(transactionBytes));

        Sha256Hash sigHash = tx.hashForWitnessSignature(
                index,
                lockScriptByteCode,
                value,
                Transaction.SigHash.ALL,
                true
        );

        return signHash(sigHash.getBytes());
    }

    /**
     *  returns a map with the Sha256 of program mapped to the signature
     * @param vtxs
     * @return
     */
    public Map<Sha256Hash, byte[]> signStack(AppTest2.ArkVirtualTransactionStack vtxs) {

        Map<Sha256Hash, Transaction> transactionMap = new HashMap<>();
        Transaction root = Transaction.read(ByteBuffer.wrap(vtxs.root));
        transactionMap.put(root.getTxId(), root);
        Map<Sha256Hash, byte[]> sigMap = new HashMap<>();

        for (AppTest2.ArkVirtualTransactionNode node : vtxs.nodes) {
            Transaction t = Transaction.read(ByteBuffer.wrap(node.transaction));
            Transaction put1 = transactionMap.put(t.getTxId(), t);
            Assertions.assertNull(put1);
        }

        for (AppTest2.ArkVirtualTransactionNode node : vtxs.nodes) {
            Transaction t = Transaction.read(ByteBuffer.wrap(node.transaction));
            List<TransactionInput> list = t.getInputs().stream().filter(ti -> transactionMap.containsKey(ti.getOutpoint().hash())).toList();

            if (list.isEmpty()) {
                System.out.println("WTF!");
            }

            for (TransactionInput ti : list) {
                TransactionOutput to = AppTest2.findOutputWitness(transactionMap.get(ti.getOutpoint().hash()), node.lock);
                byte[] put = sigMap.put(Sha256Hash.of(node.lock), signInputWitness(node.transaction, node.lock, ti.getIndex(), to.getValue()));
                Assertions.assertNull(put);
            }

            System.out.println(t);
        }

        Assertions.assertEquals(vtxs.nodes.size(), sigMap.size());

        return sigMap;
    }

    @SneakyThrows
    public byte[] createP2WPKHWitness(byte[] transactionBytes, int index, Coin value) {

        byte[] witnessSignatureBytes = signInputWitness(
                transactionBytes,
                ScriptBuilder.createP2PKHOutputScript(activeKey).program(),
                index,
                value
        );

        TransactionSignature ts = TransactionSignature.decodeFromBitcoin(witnessSignatureBytes, false, false);
        TransactionWitness witness = TransactionWitness.redeemP2WPKH(ts, activeKey);
        return witness.serialize();
    }
}
