package ae.redtoken.iz.ark.nostrtest;

//import ae.redtoken.iz.ark.nostrtest.Actor;

import com.google.common.collect.Maps;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.internal.ByteUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ae.redtoken.iz.ark.nostrtest.AppTest2.setWitness;
import static ae.redtoken.iz.ark.nostrtest.TestMessages.fromString;

public class ArkService extends Actor {

    public AppTest2.ArkCollaborativeExitAccept acec;

    public class StatefulRoundWizard extends AbstractWizard {
        private final AppTest2.ArkRoundFactory arf;

        public StatefulRoundWizard(AppTest2.ArkRoundFactory arf) {
            this.arf = arf;
        }

        public ArkRoundVTXTreeProposal createProposal() {
            List<AppTest2.ArkOnboardingRequest> aors = aorMap.values().stream().toList();

            // Now we make the next node.
            arf.createArkTree(tree, aors);

            /// Send it out for a review
            return new ArkRoundVTXTreeProposal(tree);
        }

        public AppTest2.ArkRoundStartConfirmationRequest createStartConfirmationRequest() {
            // Yes things are going peachy we have the response
            Transaction rootTx = tree.nodes.get(tree.roots.stream().findFirst().orElseThrow());

            // Service provides branch
            AppTest2.ArkVirtualTransactionStack vtxs_full = new AppTest2.ArkVirtualTransactionStack(rootTx.serialize(), tree.getSignaturesForSToSign());

            ECKey.fromPublicOnly(ArkService.this.getActivePublicKey()).getPublicKeyAsHex();
            ByteUtils.formatHex(ArkService.this.getActivePublicKey());

            AppTest2.SignatureMap signedStack = signStack(vtxs_full);

            AppTest2.UserSignaturesMap usm = new AppTest2.UserSignaturesMap();
            usm.put(ByteUtils.formatHex(getActivePublicKey()), signedStack);

            nvtaMap.forEach((byteBuffer, newVTXTreeAccept) -> usm.put(ByteUtils.formatHex(byteBuffer.array()), newVTXTreeAccept.signedStack()));

            // Service signs the S part of the tree and sends out for start signatures
            AppTest2.ArkRoundStartConfirmationRequest scr = new AppTest2.ArkRoundStartConfirmationRequest(rootTx.serialize(), usm);
//            assignWitnessToTree(nvtaMap, scr);
            assignWitnessToTree(scr);
            return scr;
        }

        //        Collection<AppTest2.StartAccept> startAccepts;
        Map<ByteBuffer, AppTest2.ArkRoundStartAccept> saMap = new HashMap<>();

        public Transaction createRootTx() {
            Transaction rootTx = tree.nodes.get(tree.roots.stream().findFirst().orElseThrow());
            // Update the witness for the rootTx based on the SA
            for (AppTest2.ArkRoundStartAccept sa : saMap.values()) {
                // Go over the response and update the witness
                for (String tops : sa.witnessMap().keySet()) {
                    TransactionOutPoint top = fromString(tops);
                    TransactionInput ti = rootTx.getInputs().stream().filter(input -> input.getOutpoint().equals(top)).findFirst().orElseThrow();
                    setWitness(ti, TransactionWitness.read(ByteBuffer.wrap(sa.witnessMap().get(top.toString()))));
                }
            }

            return rootTx;
        }

        public void on(ByteBuffer pubKey, AppTest2.ArkRoundStartAccept sa) {
            saMap.put(pubKey, sa);
        }

        Map<ByteBuffer, AppTest2.NewVTXTreeAccept> nvtaMap = new HashMap<>();

        public void on(ByteBuffer pubKey, AppTest2.NewVTXTreeAccept accept) {
            nvtaMap.put(pubKey, accept);
        }

        Map<ByteBuffer, AppTest2.ArkOnboardingRequest> aorMap = Maps.newHashMap();

        public void on(ByteBuffer pubKey, AppTest2.ArkOnboardingRequest aor) {
            aorMap.put(pubKey, aor);
        }
    }


    public ArkService(NetworkParameters params) {
        super(params);

        kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
            System.out.println("Received coin " + coin + " to " + wallet);
        });
    }

    final static Coin FUNDING_SHARD_VALUE = Coin.valueOf(0, 1);

    public void on(ByteBuffer pubKey, AppTest2.ArkCollaborativeExitRequest acer) {
        // On the other side...
        Transaction tx = Transaction.read(ByteBuffer.wrap(acer.transaction()));

        Map<Integer, byte[]> signatures = new HashMap<>();

        // Add the transaction to the tree
        tree.nodes.put(tx);

        // Input to be signed
        TransactionInput ti = tx.getInput(0);

        // Connected Output
        TransactionOutput to = tree.getOutput(ti.getOutpoint());

        if (!to.isAvailableForSpending()) {
            throw new RuntimeException("Not available to send transaction");
        }

        byte[] lock = tree.getLock(tx);

        // Marking it as spent
        to.markAsSpent(ti);

        // Service signs it
        byte[] assig = signInputWitness(tx.serialize(), lock, 0, to.getValue());
        setWitness(ti, asf.createVTXOLeafColaborativeUnlockWitness(acer.counterpartSignature(), assig, lock));

        this.acec = new AppTest2.ArkCollaborativeExitAccept(assig);
    }


//    public Collection<AppTest2.ArkVirtualTransactionNode> prepareSignatures(AppTest2.ArkTree tree) {
//
//        return tree.nodes.values().stream()
//                .filter(transaction -> !tree.roots.contains(transaction.getTxId()))
//                .map(transaction -> new AppTest2.ArkVirtualTransactionNode(
//                        transaction.transaction(),
//                        tree.getLock(transaction)))
//                .toList();
//    }
}
