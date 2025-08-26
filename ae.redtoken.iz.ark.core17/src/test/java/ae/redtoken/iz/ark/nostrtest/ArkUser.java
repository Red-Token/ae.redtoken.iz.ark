package ae.redtoken.iz.ark.nostrtest;

//import ae.redtoken.iz.ark.nostrtest.Actor;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class ArkUser extends Actor {
    ArkScriptFactory asf;
        AppTest2.ArkTree tree;
    Collection<TransactionOutput> unspentVTXOs;

    public ArkUser(NetworkParameters params) {
        super(params);
    }

    Script getVTXOLeafScript() {
        return asf.createVTXOLeafScript(getActivePublicKey());
    }

    Script getLockScript() {
        return ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(getVTXOLeafScript().program()));
    }

    public void setNewTree(AppTest2.ArkTree tree) {
        // print out the leafs
        this.tree = tree;

        unspentVTXOs = tree.nodes.values().stream()
                .flatMap(transaction -> transaction.getOutputs().stream())
                .filter(t -> Arrays.equals(t.getScriptBytes(), getLockScript().program()))
                .collect(Collectors.toList());
    }
}
