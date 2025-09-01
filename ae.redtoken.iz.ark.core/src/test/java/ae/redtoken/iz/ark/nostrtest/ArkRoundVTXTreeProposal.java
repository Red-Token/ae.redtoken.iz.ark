package ae.redtoken.iz.ark.nostrtest;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.BaseMessage;
import org.bitcoinj.core.Transaction;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public record ArkRoundVTXTreeProposal(
        Collection<Sha256Hash> roots,
        Collection<ArkLeaf> leafs,
        Map<Sha256Hash, byte[]> locks,
        Collection<byte[]> nodes) {

    public ArkRoundVTXTreeProposal(ArkTree tree) {
        this(tree.roots,
                tree.leafs,
                tree.locks,
                tree.nodes.values().stream().map(BaseMessage::serialize).toList()
        );
    }
}
