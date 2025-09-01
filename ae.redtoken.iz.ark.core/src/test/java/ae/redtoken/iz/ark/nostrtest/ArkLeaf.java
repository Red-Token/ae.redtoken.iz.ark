package ae.redtoken.iz.ark.nostrtest;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;

public record ArkLeaf(Sha256Hash hash, long index) {
    public ArkLeaf(TransactionOutPoint outpoint) {
        this(outpoint.hash(), outpoint.index());
    }
}
