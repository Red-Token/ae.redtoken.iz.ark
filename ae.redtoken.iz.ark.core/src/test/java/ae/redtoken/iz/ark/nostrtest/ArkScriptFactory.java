package ae.redtoken.iz.ark.nostrtest;

import com.google.common.collect.Lists;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArkScriptFactory {

    private final long lockTime;
    private final long nSequence;
    private final byte[] serviceKey;

    public ArkScriptFactory(long lockTime, long nSequence, byte[] serviceKey) {
        this.lockTime = lockTime;
        this.nSequence = nSequence;
        this.serviceKey = serviceKey;
    }

    public static TransactionWitness createVTXONodeUnlockWitnessScript(byte[][] userSignatures, byte[] serviceSignature, byte[] program) {

        List<byte[]> pushes = new ArrayList<>();

        pushes.add(serviceSignature);
        pushes.addAll(Arrays.asList(userSignatures));
        pushes.add(new byte[]{0x01});
        pushes.add(program);

        TransactionWitness witness = TransactionWitness.of(pushes);
        return witness;
    }

    Script createVTXONodeScript(byte[][] userKeys) {
        return createVTXONodeScript(userKeys, serviceKey, lockTime);
    }

    public Script createVTXOLeafScript(byte[] userKey) {
        return createVTXOLeaf(userKey, serviceKey, nSequence);
    }

    static Script createVTXONodeScript(ECKey[] userKeys, ECKey serviceKey, int lockTime) {
        return createVTXONodeScript(Arrays.stream(userKeys).map(ECKey::getPubKey).toArray(byte[][]::new), serviceKey.getPubKey(), lockTime);
    }

    static Script createVTXONodeScript(byte[][] userKeys, byte[] serviceKey, long lockTime) {
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder.op(ScriptOpCodes.OP_IF);

        for (byte[] key : userKeys) {
            scriptBuilder
                    .data(key)
                    .op(ScriptOpCodes.OP_CHECKSIGVERIFY);
        }

        Script redeemScript = scriptBuilder
                .data(serviceKey)
                .op(ScriptOpCodes.OP_CHECKSIG)
                // ELSE (S + Timelock case)
                .op(ScriptOpCodes.OP_ELSE)
                .number(lockTime)
                .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)
                .op(ScriptOpCodes.OP_DROP)
                .data(serviceKey)
                .op(ScriptOpCodes.OP_CHECKSIG)
                .op(ScriptOpCodes.OP_ENDIF)
                .build();

        return redeemScript;
    }

    static Script createVTXOLeaf(ECKey userKey, ECKey serviceKey, int sequenceTime) {
        byte[] userPubKey = userKey.getPubKey();
        byte[] servicePubKey = serviceKey.getPubKey();

        return createVTXOLeaf(userPubKey, servicePubKey, sequenceTime);
    }

    static Script createVTXOLeaf(byte[] userPubKey, byte[] servicePubKey, long sequenceTime) {

        Script redeemScript = new ScriptBuilder()
                // IF (A + B case)
                .op(ScriptOpCodes.OP_IF)
                .data(userPubKey)
                .op(ScriptOpCodes.OP_CHECKSIGVERIFY)
                .data(servicePubKey)
                .op(ScriptOpCodes.OP_CHECKSIG)
                // ELSE (A + Timelock case)
                .op(ScriptOpCodes.OP_ELSE)
                .number(sequenceTime)
                .op(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY)
                .op(ScriptOpCodes.OP_DROP)
                .data(userPubKey)
                .op(ScriptOpCodes.OP_CHECKSIG)
                .op(ScriptOpCodes.OP_ENDIF)
                .build();

        System.out.println(redeemScript.program().length);

        return redeemScript;
    }

    static Script createVTXONodeUnlockScript(byte[][] sigBytes, byte[] sigSByte, Script redeemScript) {
        byte[] program = redeemScript.program();

        ScriptBuilder scriptBuilder = new ScriptBuilder().data(sigSByte);

        for (byte[] sigByte : sigBytes) {
            scriptBuilder.data(sigByte);
        }

        Script inputScript = scriptBuilder
                .op(ScriptOpCodes.OP_TRUE)
                .data(program)
                .build();

        return inputScript;
    }


    static Script createVTXOLeafUnlockScript(byte[] sigAByte, byte[] sigSByte, Script redeemScript) {
        byte[] program = redeemScript.program();

        Script inputScript = new ScriptBuilder()
                .data(sigSByte)
                .data(sigAByte)
                .op(ScriptOpCodes.OP_TRUE)
                .data(program)
                .build();

        return inputScript;
    }

    static Script createVTXOLeafUnlockScript(TransactionSignature sigA, TransactionSignature sigS, Script redeemScript) {
        // This is the dual signature
        byte[] sigAByte = sigA.encodeToBitcoin();
        byte[] sigSByte = sigS.encodeToBitcoin();

        return createVTXOLeafUnlockScript(sigAByte, sigSByte, redeemScript);
    }

    static Script createVTXOLeafUnilateralUnlockScript(TransactionSignature sigA, Script redeemScript) {
        // This is the dual signature
        byte[] sigAByte = sigA.encodeToBitcoin();
        return createVTXOLeafUnilateralUnlockScript(sigAByte, redeemScript);
    }

    static Script createVTXOLeafUnilateralUnlockScript(byte[] sigAByte, Script redeemScript) {
        // This is the dual signature
        byte[] program = redeemScript.program();

        Script inputScript = new ScriptBuilder()
                .data(sigAByte)
                .data(new byte[]{})
                .data(program)
                .build();

        return inputScript;
    }

    public TransactionWitness createVTXOLeafColaborativeUnlockWitness(byte[] userSignature, byte[] serviceSignature, byte[] program) {
        List<byte[]> pushes = new ArrayList<>();

        pushes.add(serviceSignature);
        pushes.add(userSignature);
        pushes.add(new byte[]{0x01});
        pushes.add(program);

        TransactionWitness witness = TransactionWitness.of(pushes);
        return witness;
    }

    public byte[][] extractUserHashesFromVTXO(byte[] program) {
        Script programScript = Script.parse(program);
        List<byte[]> userSignatures = Lists.newArrayList();

        for (int i = 0; programScript.chunks().get(i + 2).opcode != ScriptOpCodes.OP_CHECKSIG; i += 2) {
            userSignatures.add(programScript.chunks().get(i + 1).data);
        }

        return userSignatures.toArray(new byte[0][]);
    }

    public byte[] extractServiceHashFromVTXO(byte[] program) {
        Script programScript = Script.parse(program);

        byte[] serviceHash = null;

        for (int i = 0; programScript.chunks().get(i).opcode != ScriptOpCodes.OP_CHECKSIG; i += 2) {
            serviceHash = programScript.chunks().get(i + 1).data;
        }

        return serviceHash;
    }
}
