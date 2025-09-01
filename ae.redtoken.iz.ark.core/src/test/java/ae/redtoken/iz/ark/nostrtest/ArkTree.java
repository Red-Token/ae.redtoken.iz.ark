package ae.redtoken.iz.ark.nostrtest;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;

import java.nio.ByteBuffer;
import java.util.*;

public class ArkTree {
//    public record TransactionInPoint(Sha256Hash txId, long index) {
//    }


    public ArkTree() {
    }


    public ArkTree(ArkRoundVTXTreeProposal proposal) {
        this.roots = new ArrayList<>(proposal.roots());
        this.leafs = new ArrayList<>(proposal.leafs());
        this.locks = new HashMap<>(proposal.locks());
        this.nodes = new NodeMap();

        proposal.nodes().forEach((sha256Hash, bytes) -> {
            nodes.put(Transaction.read(ByteBuffer.wrap(bytes)));
        });
    }

    static class NodeMap extends HashMap<Sha256Hash, Transaction> {
        Transaction put(Transaction transaction) {
            return put(transaction.getTxId(), transaction);
        }
    }

    Collection<Sha256Hash> roots;
    NodeMap nodes = new NodeMap();

    //Locks is a hashmap where you lookup the lock program based on the output that it locks
    //So if you have an output, the locks contains the program for the input, to be signed to unlock the lock
    Map<Sha256Hash, byte[]> locks = new HashMap<>();
    Collection<ArkLeaf> leafs = new ArrayList<>();

    TransactionOutput getOutput(TransactionOutPoint top) {
        return nodes.get(top.hash()).getOutput(top.index());
    }

    /**
     * Gets the lock that unlocks the Output behind this OutPoint
     *
     * @param top Outpoint to be unlocked
     * @return
     */
    byte[] getLock(TransactionOutPoint top) {
        return locks.get(Sha256Hash.wrap(ScriptPattern.extractHashFromP2SH(Script.parse(getOutput(top).getScriptBytes()))));
    }

    byte[] getLock(Transaction transaction) {
        return getLock(transaction.getInput(0).getOutpoint());
    }

    Collection<AppTest2.ArkVirtualTransactionNode> getSignaturesForSToSign() {
        return nodes.values().stream()
                .filter(transaction -> !roots.contains(transaction.getTxId()))
                .map(transaction -> new AppTest2.ArkVirtualTransactionNode(
                        transaction.serialize(),
                        getLock(transaction)))
                .toList();
    }

//    Collection<Transaction> getSpendPath(TransactionOutput leaf) {
//        List<Transaction> tl = new ArrayList<>();
//
//        for (Transaction t = nodes.get(leaf.getOutPointFor().hash());
//             !roots.contains(t.getTxId());
//             t = nodes.get(t.getInput(0).getOutpoint().hash())) {
//            tl.addFirst(t);
//        }
//
//        return tl;
//    }
}
