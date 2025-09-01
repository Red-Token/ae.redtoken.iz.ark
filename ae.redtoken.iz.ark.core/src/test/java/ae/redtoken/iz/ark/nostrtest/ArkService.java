package ae.redtoken.iz.ark.nostrtest;

//import ae.redtoken.iz.ark.nostrtest.Actor;

import lombok.SneakyThrows;
import org.bitcoinj.base.Coin;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.SendRequest;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ae.redtoken.iz.ark.nostrtest.AppTest2.assignWitness;
import static ae.redtoken.iz.ark.nostrtest.AppTest2.setWitness;

public class ArkService extends Actor {

    public class StatefulRoundWizard {
        private final AppTest2.ArkRoundFactory arf;

        public StatefulRoundWizard(AppTest2.ArkRoundFactory arf) {
            this.arf = arf;
        }

        public AppTest2.ArkRoundVTXTreeProposal createProposal(List<AppTest2.ArkOnboardingRequest> aors) {

            // Now we make the next node.
            arf.createArkTree(tree, aors);

            /// Send it out for a review
            return new AppTest2.ArkRoundVTXTreeProposal(tree);
        }

        public AppTest2.StartConfirmationRequest createStartConfirmationRequest() {
            // Yes things are going peachy we have the response
            Transaction rootTx = tree.nodes.get(tree.roots.stream().findFirst().orElseThrow());

            // Service provides branch
            AppTest2.ArkVirtualTransactionStack vtxs_full = new AppTest2.ArkVirtualTransactionStack(rootTx.serialize(), tree.getSignaturesForSToSign());

            // Service signs the S part of the tree and sends out for start signatures
            return new AppTest2.StartConfirmationRequest(rootTx.serialize(), signStack(vtxs_full));
        }

        public void assignWitnessToTree(Map<ByteBuffer, AppTest2.NewVTXTreeAccept> nvtaMap, AppTest2.StartConfirmationRequest scr) {
            // The tree is updated based on the SCR and nvtaMap
            for (Transaction node : tree.nodes.values()) {
                // Filter out the root node
                if (tree.roots.contains(node.getTxId()))
                    continue;

                for (int i = 0; i < node.getInputs().size() - 1; i++) {
                    TransactionInput input = node.getInput(i);
                    assignWitness(input, tree, arf.asf, nvtaMap, scr);
                }
            }
        }

        //        Collection<AppTest2.StartAccept> startAccepts;
        Map<ByteBuffer, AppTest2.StartAccept> saMap = new HashMap<>();

        public Transaction createRootTx() {
            Transaction rootTx = tree.nodes.get(tree.roots.stream().findFirst().orElseThrow());
            // Update the witness for the rootTx based on the SA
            for (AppTest2.StartAccept sa : saMap.values()) {
                // Go over the response and update the witness
                for (TransactionOutPoint top : sa.witnessMap().keySet()) {
                    TransactionInput ti = rootTx.getInputs().stream().filter(input -> input.getOutpoint().equals(top)).findFirst().orElseThrow();
                    setWitness(ti, TransactionWitness.read(ByteBuffer.wrap(sa.witnessMap().get(top))));
                }
            }

            return rootTx;
        }

        public void on(ByteBuffer pubKey, AppTest2.StartAccept sa) {
            saMap.put(pubKey, sa);
        }

        Map<ByteBuffer, AppTest2.NewVTXTreeAccept> nvtaMap = new HashMap<>();

        public void on(ByteBuffer pubKey, AppTest2.NewVTXTreeAccept accept) {
            nvtaMap.put(pubKey, accept);
        }
    }

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
