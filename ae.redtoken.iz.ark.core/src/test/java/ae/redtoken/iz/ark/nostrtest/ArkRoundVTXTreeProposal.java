package ae.redtoken.iz.ark.nostrtest;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.BaseMessage;
import org.bitcoinj.core.Transaction;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public record ArkRoundVTXTreeProposal(
        Collection<Sha256Hash> roots,
        Map<Sha256Hash, byte[]> nodes,
        Map<Sha256Hash, byte[]> locks,
        Collection<ArkLeaf> leafs) {

    public ArkRoundVTXTreeProposal(ArkTree tree) {
        this(tree.roots,
                tree.nodes.values().stream().collect(Collectors.toMap(Transaction::getTxId, BaseMessage::serialize)),
                tree.locks,
                tree.leafs);
    }
}
