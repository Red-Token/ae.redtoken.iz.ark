package ae.redtoken.iz.ark.nostrtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import nostr.event.Kind;
import nostr.event.impl.Filters;
import nostr.event.impl.GenericEvent;
import org.bitcoin.tfw.ltbc.tc.LTBCMainTestCase;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Stream;

import static ae.redtoken.iz.ark.nostrtest.TestNostr.RELAYS;


/**
 *
 */
public class AppTest2 extends LTBCMainTestCase {

    public static class ArkRoundFactory {
        final NetworkParameters params;
        final ArkScriptFactory asf;
        final ArkService arkService;

        public ArkRoundFactory(NetworkParameters params, ArkScriptFactory asf, ArkService arkService) {
            this.params = params;
            this.asf = asf;
            this.arkService = arkService;
        }

        static class Zel {
            TransactionOutput to;
            byte[] program;
            ArkTree at;

            public Zel(ArkTree at, TransactionOutput to, byte[] program) {
                this.at = at;
                this.to = to;
                this.program = program;
            }
        }

        Zel createArkTreeRoot(List<FoundingMember> foundingMembers, Collection<TransactionOutput> foundingOutputs) {

            ArkTree tree = new ArkTree();

            // rec create the tree
            Transaction tx = new Transaction();
//            Transaction tx = new Transaction(params);
            tx.setVersion(2);

            foundingOutputs.forEach(tx::addInput);

            byte[] rs = asf.createVTXONodeScript(foundingMembers.stream().map(m -> m.key).toArray(byte[][]::new)).getProgram();

            // Create the output and send in the hash of the script into that output.
            TransactionOutput nodeOutput = tx.addOutput(Coin.valueOf(foundingMembers.stream().mapToLong(m -> m.value.value).sum()), ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(rs)));
            fund(tx, arkService);

            tree.locks.put(Sha256Hash.of(rs), rs);

//            createArkTreeNode(tree, foundingMembers, nodeOutput);
            return new Zel(tree, nodeOutput, rs);
        }

        Sha256Hash createArkTreeNode(ArkTree tree, List<FoundingMember> members, TransactionOutput output) {
            record SubNode(List<FoundingMember> list, TransactionOutput output) {
            }

            // rec create the tree
            Transaction tx = new Transaction();
            tx.setVersion(2);
            TransactionInput ti = tx.addInput(output);
            output.markAsSpent(ti);

            Collection<SubNode> subNodes = new ArrayList<>();

            int chunkSize = members.size() / 2;

            for (int i = 0; i < members.size(); i += chunkSize) {
                List<FoundingMember> list = members.subList(i, i + chunkSize);

                if (list.size() <= 1) {
                    FoundingMember member = list.getFirst();

                    Script rs = asf.createVTXOLeafScript(member.key);
                    tree.locks.put(Sha256Hash.of(rs.getProgram()), rs.getProgram());
                    TransactionOutput leafOutput = tx.addOutput(
                            member.value,
                            ScriptBuilder.createP2WSHOutputScript(rs));

                    tree.leafs.add(new ArkTree.ArkLeafs(leafOutput.getOutPointFor(), rs.getProgram()));

                } else {

                    Script rs = asf.createVTXONodeScript(list.stream().map(m -> m.key).toArray(byte[][]::new));
                    tree.locks.put(Sha256Hash.of(rs.getProgram()), rs.getProgram());
                    TransactionOutput nodeOutput = tx.addOutput(
                            Coin.valueOf(list.stream().mapToLong(m -> m.value.value).sum()),
                            ScriptBuilder.createP2WSHOutputScript(rs));

                    subNodes.add(new SubNode(list, nodeOutput));
                }
            }

            fund(tx, arkService);

            tree.nodes.put(tx);

            subNodes.forEach(subNode -> {
                createArkTreeNode(tree, subNode.list, subNode.output);
            });

            return tx.getTxId();
        }

        private void fund(Transaction tx, Actor arkService) {
            System.out.println("To be spent: " + arkService.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getValue().equals(Coin.valueOf(0, 1))).count());

            // Add the funding input, post transaction signature
            TransactionOutput output = arkService.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getValue().equals(Coin.valueOf(0, 1)) && transactionOutput.isAvailableForSpending()).findFirst().orElseThrow();
            TransactionInput fti1 = tx.addInput(output);
            output.markAsSpent(fti1);
            tx.replaceInput(fti1.getIndex(), arkService.signSpendingInput(fti1));
//
//        ECKey key = arkService.kit.wallet().findKeyFromPubKeyHash(output.getScriptPubKey().getPubKeyHash(), Script.ScriptType.P2PKH);
//
//        // 2. The P2PKH scriptPubKey (the one you're spending from)
//        Script scriptPubKey = ScriptBuilder.createP2PKHOutputScript(key);
//
//        // 3. Sign the input
//        int inputIndex = fti1.getIndex();  // adjust if needed
//        Transaction.SigHash sigHash = Transaction.SigHash.ALL;
//        boolean anyoneCanPay = true;
//
//        // 4. Create the hash for signature
//        Sha256Hash sigHashBytes = tx.hashForSignature(inputIndex, scriptPubKey, sigHash, anyoneCanPay);
//
//        // 5. Create the ECDSA signature
//        ECKey.ECDSASignature signature = key.sign(sigHashBytes);
//        TransactionSignature txSig = new TransactionSignature(signature, sigHash, anyoneCanPay);
//
//        // 6. Create scriptSig (the unlocking script)
//        Script inputScript = ScriptBuilder.createInputScript(txSig, key);
//
//        fti1.setScriptSig(inputScript);

            // Sign the funding input
//        SendRequest sr = SendRequest.forTx(tx);
//        sr.ensureMinRequiredFee = false;
//        arkService.kit.wallet().signTransaction(sr);
        }
    }

    record FoundingMember(Coin value, byte[] key) {
    }

    public static class SignatureRequest {
        final byte[] program;
        final Collection<byte[]> signatures = new ArrayList<>();

        public SignatureRequest(byte[] program, byte[]... signatures) {
            this.program = program;
            this.signatures.addAll(Arrays.asList(signatures));
        }
    }

    static TransactionOutput findOutput(Transaction tx, Script rs) {
        return tx.getOutputs().stream().filter(o -> Arrays.equals(o.getScriptBytes(), ScriptBuilder.createP2SHOutputScript(rs).getProgram())).findFirst().orElseThrow();
    }

    public static TransactionOutput findOutputWitness(Transaction tx, byte[] rs) {
        return tx.getOutputs().stream().filter(o -> Arrays.equals(o.getScriptBytes(), ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(rs)).getProgram())).findFirst().orElseThrow();
    }

    public static class ArkVirtualTransactionNode {
        final byte[] transaction;
        final byte[] program;

        public ArkVirtualTransactionNode(byte[] transaction, byte[] program) {
            this.transaction = transaction;
            this.program = program;
        }
    }

    public static class ArkVirtualTransactionStack {
        final byte[] root;
        final Collection<ArkVirtualTransactionNode> nodes;

        ArkVirtualTransactionStack(byte[] root, Collection<ArkVirtualTransactionNode> nodes) {
            this.root = root;
            this.nodes = nodes;
        }
    }

    static class ArkVirtualTransactionTreeSignatureRequest {
    }

    static class ArkVirtualTransactionTreeActivated {
    }

    static class ArkVirtualTransactionSignatureResponse {
        final Map<String, byte[]> signatures = new HashMap<>();
    }

    static class ArkTree {
        public ArkService arkService;

        static class NodeMap extends HashMap<Sha256Hash, Transaction> {
            Transaction put(Transaction transaction) {
                return put(transaction.getTxId(), transaction);
            }
        }

        static class ArkLeafs {
            TransactionOutPoint outpoint;
            byte[] program;

            public ArkLeafs(TransactionOutPoint outpoint, byte[] program) {
                this.outpoint = outpoint;
                this.program = program;
            }
        }

        Collection<Sha256Hash> roots;
        NodeMap nodes = new NodeMap();
        Map<Sha256Hash, byte[]> locks = new HashMap<>();
        Collection<ArkLeafs> leafs = new ArrayList<>();

        TransactionOutput getOutput(TransactionOutPoint top) {
            return nodes.get(top.getHash()).getOutput(top.getIndex());
        }
    }


    /**
     *
     * This is a simple scenario that creates a mini Sync-ARK with four users
     * <p>
     * Alice, Bob, Carol and Dave. They all start the sARK with 1.0 BTC each
     * Freddy and Eve join later.
     * <p>
     * Set up the ARK
     * <p>
     * Alice sends money to Bob
     * <p>
     * Eve joins later
     * <p>
     * Carol sends money to Fred
     * <p>
     * Round ends, Bob wants to offboard 0.5 BTC
     *
     * @throws Exception
     */

    @Test
    public void test2() throws Exception {
        RegTestParams params = RegTestParams.get();

        ArkService arkService = new ArkService(params);
        ArkUser alice = new ArkUser(params);
        ArkUser bob = new ArkUser(params);
        ArkUser carol = new ArkUser(params);
        ArkUser david = new ArkUser(params);
        ArkUser eve = new ArkUser(params);
        ArkUser freddy = new ArkUser(params);

        ArkUser[] users = Arrays.asList(alice, bob, carol, david, eve, freddy).toArray(new ArkUser[0]);

        final double coinsToSendToArkService = 10;
        final double coinsToSendToUsers = 1;

        Assertions.assertEquals(Coin.ZERO, arkService.kit.wallet().getBalance());

        this.ltbc.sendTo(arkService.kit.wallet().freshReceiveAddress().toString(), coinsToSendToArkService);

        for (Actor user : users) {
            this.ltbc.sendTo(user.kit.wallet().freshReceiveAddress().toString(), coinsToSendToUsers);
        }

        this.ltbc.mine(16);

        // We wait for 1 second here
        Thread.sleep(1000);

        // TODO fix race condition here
        Assertions.assertEquals(Coin.valueOf((int) coinsToSendToArkService, 0), arkService.kit.wallet().getBalance());

        for (Actor user : users) {
            Assertions.assertEquals(Coin.valueOf((int) coinsToSendToUsers, 0), user.kit.wallet().getBalance());
        }

        arkService.createFundingShards(10);
        mineAndWait();

        // Create the root node

        // Generate keys
        ECKey keyS = arkService.activeKey;

        int timeLockBlocks = 10;
        int seqLockBlocks = 100;

        // Here we have one ASF for all the users
        final ArkScriptFactory asf = new ArkScriptFactory(seqLockBlocks, timeLockBlocks, keyS.getPubKey());
//        final Map<Sha256Hash, byte[]> programs = new HashMap<>();

        Arrays.stream(users).forEach(user -> user.asf = asf);

        // Start the ARK
        /**
         *  ARK Initiate
         *      - Inform that a new ARK is starting and the minimumEntry to join
         *
         *  ARK OnboardingRequest
         *      - Send in your start key and the Inputs to join with
         *
         *  ARK StartConfirmationRequest
         *      - Send out the start node for signing
         *
         *  ARK StartAccept
         *      - User sends the signed Inputs to start the ARK
         *
         *  ARK Start
         *      - deposit the root node
         *      - send the start node to the users
         */

        {
            List<ArkUser> initiators = List.of(alice, bob, carol, david);

            record ArkInitiate(Coin minValue) {
            }

            record ArkOnboardingRequest(ECKey key, List<TransactionInput> inputs) {
            }

            ArkInitiate aim = new ArkInitiate(Coin.valueOf(0, 10));


            ///  Create the root node
            // Create the transaction
//            Transaction ctx = new Transaction(params);

            // S issues a ArkInitiation

            // Users ask to join

            Transaction rootTx = new Transaction();
            rootTx.setVersion(2);

            ArkRoundFactory arf = new ArkRoundFactory(params, asf, arkService);

            // This is the output that is used to FUND the rootNode (the fundingInput in the rootNode takes its capital from here
            TransactionOutput arkFundingOutput;

            Script arkFundingLockScript = ScriptBuilder.createP2WPKHOutputScript(arkService.activeKey);
//            Script arkFundingRs = ScriptBuilder.createP2PKHOutputScript(arkService.activeKey);

            // Let's fund the founding output
            {
                Transaction arkFundingTx = new Transaction();
                arkFundingTx.setVersion(2);

//                arkFundingOutput = arkFundingTx.addOutput(Coin.valueOf(4, 0), arkService.kit.wallet().freshReceiveAddress());
                arkFundingOutput = arkFundingTx.addOutput(Coin.valueOf(4, 0), arkFundingLockScript);

                SendRequest sr = SendRequest.forTx(arkFundingTx);
                sr.feePerKb = Coin.valueOf(1000);
                arkService.kit.wallet().completeTx(sr);

                // Send it out
                arkService.kit.peerGroup().broadcastTransaction(sr.tx);

                // Mine
                mineAndWait();
            }

            // Now we have money in our fundingOutput

            // Start creating the tree
            FoundingMember afm = new FoundingMember(Coin.valueOf(1, 0), alice.getActivePublicKey());
            FoundingMember bfm = new FoundingMember(Coin.valueOf(1, 0), bob.getActivePublicKey());
            FoundingMember cfm = new FoundingMember(Coin.valueOf(1, 0), carol.getActivePublicKey());
            FoundingMember dfm = new FoundingMember(Coin.valueOf(1, 0), david.getActivePublicKey());

            List<FoundingMember> fml = List.of(afm, bfm, cfm, dfm);
            Coin value = fml.stream().map(FoundingMember::value).reduce(Coin.ZERO, Coin::add);

            // Create a VTXO based on userKeys
            byte[] rs1 = asf.createVTXONodeScript(fml.stream().map(FoundingMember::key).toArray(byte[][]::new)).program();

            // Here we should fund the ARK from the user
            // Create the output and send in the hash of the script into that output.
            TransactionOutput rootTo = rootTx.addOutput(value, ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(rs1)));

            TransactionInput rootTi = rootTx.addInput(arkFundingOutput);
            arkFundingOutput.markAsSpent(rootTi);

            // Add a feeInput to transaction.
            fund(rootTx, arkService);

            ArkTree tree = new ArkTree();

            tree.arkService = arkService;
            tree.roots = Collections.singletonList(rootTx.getTxId());
            tree.nodes.put(rootTx.getTxId(), rootTx);

            // Now we make the next node.
            {
                Sha256Hash hs = arf.createArkTreeNode(tree, fml, rootTo);

                // On the ArkService side
                Transaction vtx1 = tree.nodes.get(hs);

                // Now we create the outputs
                // A + B + S | S + T=100
//                byte[] rs1_1 = tree.locks.get(Sha256Hash.of(asf.createVTXONodeScript(Stream.of(alice, bob).map(Actor::getActivePublicKey).toArray(byte[][]::new)).program()));

                // Create the output and send in the hash of the script into that output.
                // C + D + S | S + T=100
//                byte[] rs1_2 = tree.locks.get(Sha256Hash.of(asf.createVTXONodeScript(Stream.of(carol, david).map(Actor::getActivePublicKey).toArray(byte[][]::new)).program()));

                TransactionInput ti1 = vtx1.getInput(0);
                Transaction vtx1_1 = vtx1.getOutput(0).getSpentBy().getParentTransaction();

                // Create the leafs
                // Now we create the outputs
                // A + S | A + dT=10
                byte[] rs1_1_1 = tree.locks.get(Sha256Hash.of(asf.createVTXOLeafScript(alice.getActivePublicKey()).program()));

                // remember it
//                programs.put(Sha256Hash.of(rs1_1_1), rs1_1_1);

                // B + S | B + dT=10
//                byte[] rs1_1_2 = tree.locks.get(Sha256Hash.of(asf.createVTXOLeafScript(bob.getActivePublicKey()).program()));

                // Create the output and send in the hash of the script into that output.

                // remember it
//                programs.put(Sha256Hash.of(rs1_1_2), rs1_1_2);

                // Connect the input
                // TODO: This is a hack!
                TransactionInput ti_1_1 = vtx1_1.getInput(0);

                // Fund the transaction
                Transaction vtx1_2 = vtx1.getOutput(1).getSpentBy().getParentTransaction();

                // Create the leafs
                // Now we create the outputs
                // C + S | C + dT=10
                byte[] rs1_2_1 = tree.locks.get(Sha256Hash.of(asf.createVTXOLeafScript(carol.getActivePublicKey()).program()));

                // remember it
//                programs.put(Sha256Hash.of(rs1_2_1), rs1_2_1);

                // D + S | D + dT=10
                byte[] rs1_2_2 = tree.locks.get(Sha256Hash.of(asf.createVTXOLeafScript(david.getActivePublicKey()).program()));
                // remember it
//                programs.put(Sha256Hash.of(rs1_2_2), rs1_2_2);

                // Connect the input
                TransactionInput ti_1_2 = vtx1_2.getInput(0);

                byte[] rs1_1 = tree.locks.get(Sha256Hash.of(asf.createVTXONodeScript(Stream.of(alice, bob).map(Actor::getActivePublicKey).toArray(byte[][]::new)).program()));


                // Alice and Bobs branch
                ArkVirtualTransactionStack vtxs_1_1 = new ArkVirtualTransactionStack(rootTx.serialize(), List.of(
                        new ArkVirtualTransactionNode(vtx1.serialize(), rs1),
                        new ArkVirtualTransactionNode(vtx1_1.serialize(), rs1_1)
                ));

                // Alice signs the tree
                Map<Sha256Hash, byte[]> aliceSignatures = alice.signStack(vtxs_1_1);

                // Bob signs the tree
                Map<Sha256Hash, byte[]> bobSignatures = bob.signStack(vtxs_1_1);

                // Carols and Davids branch
                byte[] rs1_2 = tree.locks.get(Sha256Hash.of(asf.createVTXONodeScript(Stream.of(carol, david).map(Actor::getActivePublicKey).toArray(byte[][]::new)).program()));

                ArkVirtualTransactionStack vtxs_1_2 = new ArkVirtualTransactionStack(rootTx.serialize(), List.of(
                        new ArkVirtualTransactionNode(vtx1.serialize(), rs1),
                        new ArkVirtualTransactionNode(vtx1_2.serialize(), rs1_2)
                ));

                // Carol signs the tree
                Map<Sha256Hash, byte[]> carolSignatures = carol.signStack(vtxs_1_2);

                // David signs the tree
                Map<Sha256Hash, byte[]> davidSignatures = david.signStack(vtxs_1_2);

                // Service provides branch
                ArkVirtualTransactionStack vtxs_full = new ArkVirtualTransactionStack(rootTx.serialize(), List.of(
                        new ArkVirtualTransactionNode(vtx1.serialize(), rs1),
                        new ArkVirtualTransactionNode(vtx1_1.serialize(), rs1_1),
                        new ArkVirtualTransactionNode(vtx1_2.serialize(), rs1_2)
                ));

                // S signs the tree
                Map<Sha256Hash, byte[]> arkServiceSignatures = arkService.signStack(vtxs_full);

                // Add the signatures to the root node
                {
                    byte[] program = rs1;

                    Sha256Hash programHash = Sha256Hash.of(program);
                    TransactionWitness witness = ArkScriptFactory.createVTXONodeUnlockWitnessScript(
                            new byte[][]{
                                    davidSignatures.get(programHash),
                                    carolSignatures.get(programHash),
                                    bobSignatures.get(programHash),
                                    aliceSignatures.get(programHash)
                            },
                            arkServiceSignatures.get(programHash),
                            program);

                    setWitness(ti1, witness);
                }

                // Sign the input by everybody
                {
                    byte[] program = rs1_1;
                    Sha256Hash programHash = Sha256Hash.of(program);
                    TransactionWitness witness = ArkScriptFactory.createVTXONodeUnlockWitnessScript(
                            new byte[][]{
                                    bobSignatures.get(programHash),
                                    aliceSignatures.get(programHash)
                            },
                            arkServiceSignatures.get(programHash),
                            program);
                    setWitness(ti_1_1, witness);
                }

                // Sign the input by everybody
                {
                    byte[] program = rs1_2;
                    Sha256Hash programHash = Sha256Hash.of(program);

                    TransactionWitness witness = ArkScriptFactory.createVTXONodeUnlockWitnessScript(
                            new byte[][]{
                                    davidSignatures.get(programHash),
                                    carolSignatures.get(programHash)
                            },
                            arkServiceSignatures.get(programHash),
                            program);
                    setWitness(ti_1_2, witness);
                }

                // Now let's complete and fund this transaction
                // Todo: this part here needs to be rewritten to work with the signing strategy
                {
                    // Create the witness
                    // TODO move this to the scriptfactory
                    byte[] witnessBytes = arkService.createP2WPKHWitness(rootTx.serialize(), rootTi.getIndex(), arkFundingOutput.getValue());

                    // Sign the root transaction
                    setWitness(rootTi, TransactionWitness.read(ByteBuffer.wrap(witnessBytes)));

                    // Send it out
                    arkService.kit.peerGroup().broadcastTransaction(rootTx);

                    // Mine
                    mineAndWait();
                }

                ///  The ARK Round is deposit

                alice.setNewTree(tree);
                bob.setNewTree(tree);
                carol.setNewTree(tree);
                david.setNewTree(tree);

                for (ArkUser user : List.of(alice, bob, carol, david)) {
                    Assertions.assertEquals(1, user.unspentVTXOs.size());
                }

                // Collaborative exit
                // Agreed exit for A
                Transaction vtx1_1_1 = new Transaction();
//                Transaction vtx1_1_1 = new Transaction(params);
                vtx1_1_1.setVersion(2);

                // Create the output and send in the hash of the script into that output.
                vtx1_1_1.addOutput(Coin.valueOf(0, 66), alice.kit.wallet().freshReceiveAddress());

                TransactionOutput output = alice.unspentVTXOs.stream().findFirst().orElseThrow();

                // Connect the input
                TransactionInput ti_1_1_1 = vtx1_1_1.addInput(output);
//                TransactionInput ti_1_1_1_2 = vtx1_1_1.addInput(output2);
                fund(vtx1_1_1, arkService);

                // Lets rool!
                byte[] sigABin = alice.signInputWitness(vtx1_1_1.serialize(), rs1_1_1, ti_1_1_1.getIndex(), Objects.requireNonNull(ti_1_1_1.getConnectedOutput()).getValue());

                // Collaborative exit request
                // Set of UTXO:s to exit, OutPoint
                // This is on the ARK Service side

                Map<Integer, SignatureRequest> programMap = Map.of(ti_1_1_1.getIndex(), new SignatureRequest(rs1_1_1, sigABin));

                Transaction tx = Transaction.read(ByteBuffer.wrap(vtx1_1_1.serialize()));
//                Transaction tx = new Transaction(params, vtx1_1_1.serialize());
                Map<Integer, byte[]> signatures = new HashMap<>();

                tree.nodes.put(vtx1_1_1.getTxId(), vtx1_1_1);

                for (Integer index : programMap.keySet()) {
                    TransactionInput ti = tx.getInput(index);
                    TransactionOutput to = tree.getOutput(ti.getOutpoint());

                    if (!to.isAvailableForSpending()) {
                        throw new RuntimeException("Not available to send transaction");
                    }

                    to.markAsSpent(ti);
                    signatures.put(index, arkService.signInputWitness(tx.serialize(), programMap.get(index).program, index, to.getValue()));
                }


                // Sign the input by everybody
                {
                    setWitness(ti_1_1_1, asf.createVTXOLeafColaborativeUnlockWitness(sigABin, signatures.get(ti_1_1_1.getIndex()), rs1_1_1));
                }

                // Let's make an on-chain charity output
//                fundAndSend(vtx1, arkService, rs1, alice);
//                fundAndSend(vtx1_1, arkService, rs1_1, alice);

                send(vtx1, arkService, null, alice);
                send(vtx1_1, arkService, null, alice);
                send(vtx1_1_1, arkService, null, alice);
//
//                // Send it in
//                {
////                    SendRequest sr = SendRequest.forTx(vtx1_1_1);
////                    sr.feePerKb = Coin.valueOf(1000);
////                    arkService.kit.wallet().completeTx(sr);
//                    vtx1_1_1.getInput(0).getScriptSig().correctlySpends(vtx1_1_1, 0, ScriptBuilder.createP2SHOutputScript(rs1_1_1), Script.ALL_VERIFY_FLAGS);
//
//                    // Send it out
//                    arkService.kit.peerGroup().broadcastTransaction(vtx1_1_1);
//                    System.out.println(vtx1_1_1);
//
//                    // Mine
//                    Thread.sleep(5000);
//                    ltbc.mine(16);
//                    Thread.sleep(5000);
//                    System.out.println(alice.kit.wallet().getBalance());
//                }

                System.out.println("HLLSLSSL");
                Assertions.assertEquals(166000000, alice.kit.wallet().getBalance().value);
            }
        }

//        Script redeemScript = ArkScriptFactory.createVTXOLeaf(alice.activeKey, keyS, timeLockBlocks);
//        Script p2shScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
//
//        Transaction txOut = new Transaction(params);
//        TransactionOutput output = txOut.addOutput(Coin.valueOf(1, 0), p2shScript);
//
//        SendRequest sr = SendRequest.forTx(txOut);
//        sr.feePerKb = Coin.valueOf(1000);
//        arkService.kit.wallet().completeTx(sr);
//
//        arkService.kit.wallet().addWatchedScripts(List.of(p2shScript));
//        alice.kit.wallet().addWatchedScripts(List.of(p2shScript));
//
//        arkService.kit.peerGroup().broadcastTransaction(sr.tx);
//
//        Thread.sleep(5000);
//        ltbc.mine(16);
//        Thread.sleep(5000);
//
//        TransactionOutput to = arkService.kit.wallet().getWatchedOutputs(false).stream()
//                .filter(transactionOutput -> transactionOutput.getScriptPubKey().equals(p2shScript))
//                .findFirst()
//                .orElseThrow();
//
////        arkService.kit.wallet().getWatchedScripts().forEach(script -> {
////            System.out.println(script.equals(p2shScript));
////        });
//
////        TransactionOutput to = arkService.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getScriptPubKey().equals(p2shScript)).findFirst().orElseThrow();
//
//        System.out.println(alice.kit.wallet().getBalance());
//
//        Transaction tx = new Transaction(params);
//
//        // This enables lockTime ie blockHeight lock
////        tx.setLockTime(10);
//
//        // And yes we should always  do this
//        tx.setVersion(2);
//        tx.addInput(to.getOutPointFor().getConnectedOutput()); // OutPoint from the UTXO (txHash + outputIndex)
//
//        // This also enables lockTime ie relative timeLock
////        tx.getInput(0).setSequenceNumber(10);
//
//        Address recipientAddress = arkService.kit.wallet().freshReceiveAddress();
//        tx.addOutput(Coin.valueOf(50_000), recipientAddress);
//        tx.addOutput(Coin.valueOf(99_900_666), alice.kit.wallet().freshReceiveAddress());
//
//        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
//        TransactionSignature sigS = new TransactionSignature(keyS.sign(sighash), Transaction.SigHash.ALL, false);
//
//        byte[] sigABin = alice.sign(sighash.getBytes());
//        byte[] sigSBin = sigS.encodeToBitcoin();
//
////        Script inputScript = ArkScriptFactory.createVTXOUnilateralUnlock(sigABin, redeemScript);
////        Script inputScript = ArkScriptFactory.createVTXOUnlock(sigA, sigS, redeemScript);
//        Script inputScript = ArkScriptFactory.createVTXOLeafUnlockScript(sigABin, sigSBin, redeemScript);
//        tx.getInput(0).setScriptSig(inputScript);
//
//        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
//        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
//        arkService.kit.peerGroup().broadcastTransaction(tx);
//        // Broadcast it
//
//        System.out.println(tx);
//
//        ltbc.mine(6);
//        Thread.sleep(5000);
//
//        System.out.println(alice.kit.wallet().getBalance());
//
//        Assertions.assertEquals(199900666, alice.kit.wallet().getBalance().value);

//        var aliceIdentity = Identity.generateRandomIdentity();
//        var eveIdentity = Identity.generateRandomIdentity();

        /**
         *  Step 1: Eve creates a quotation
         */

        ObjectMapper om = new ObjectMapper();
        TestNostr.ArkQuotationContent aqc = new TestNostr.ArkQuotationContent();

        aqc.amount = 30000;
        aqc.pubkey = "WHATEVER";
        aqc.arks.includeOnly = true;
        aqc.arks.include = new String[]{"myarc"};
        aqc.offer.setCurrencyCode(Currency.getInstance("USD").getCurrencyCode());
        aqc.offer.items = new TestNostr.ArkOfferItems[]{
                new TestNostr.ArkOfferItems("Pepperoni", 1, 3.0)
        };
        aqc.offer.vat = "5%";

        String offer = om.writeValueAsString(aqc);
        System.out.println(offer);

        TestNostr.NIP0666<TestNostr.NIP0666ArkQuotationEvent> nip0666Stack = new TestNostr.NIP0666<>();
        nip0666Stack.setSender(eve.identity);
        nip0666Stack.setRelays(RELAYS);
        TestNostr.NIP0666ArkQuotationEventFactory xy = new TestNostr.NIP0666ArkQuotationEventFactory(eve.identity, offer);
        nip0666Stack.setEvent(xy.create());
        nip0666Stack.signAndSend();

        /**
         *  Step 2: Alice scans the bitcoin URL and fetches the offer
         */

        String id = nip0666Stack.getEvent().getId();

        TestNostr.NIP0666<TestNostr.NIP0666ArkQuotationEvent> aliceNip0666Stack = new TestNostr.NIP0666<>();
        aliceNip0666Stack.setRelays(RELAYS);
        aliceNip0666Stack.setSender(alice.identity);
        GenericEvent ge = new GenericEvent();
        ge.setId(id);
        Filters filters2 = Filters.builder().events(List.of(ge)).kinds(List.of(Kind.ARK_QUOTATION)).build();
        String subId2 = "sub_" + alice.identity.getPublicKey();

        BlockingQueue<GenericEvent> queue = new ArrayBlockingQueue<>(1);

        EventCustomHandler2.handlers.put(subId2, (event, message, relay) -> {
            queue.add((GenericEvent) event);
        });

        aliceNip0666Stack.send(filters2, subId2);

        GenericEvent take = queue.take();
        System.out.println(take);

        System.out.println("THE END!");

        /*
         *  Scenario 1
         *
         *  Alice, Bob, Carol, Dave have 1 BTC each in an ARK managed by arkService.
         *
         *  Alice then sends 0.5 BTC to Eve, and Freddy joins the group with 1 BTC. And finally Clare decide she wants to leave with 0.3 BTC.
         *
         *  A new round is created by arkService.
         *
         *  First arkService proposes a new foundation tree.
         *
         *  All parties are then proposes to sign, and fund the nodes in the tree from there current VTXO:s exit transaction.
         *
         *  The makes the root of the round tree valid.
         *
         *  In the second stage tha participants are asked to sign the root nodes of the current round.
         *
         *  Once this is done arkService deposits the tree transition node to the blockchain.
         *
         *  Noster based communication
         *
         *  Eve sends a payment request using a bitcoin uri
         *
         *  bitcoin://#bitcoinaddress#?amount=0.01&lightning=#lightninginvoice#&ark=#npub#
         *
         *  payment request  {
         *      amount: #######
         *      arks: {
         *          include: [ ark1_npub, ark2_npub ]
         *          exclude: [ ark2_npub, ark3_npub ]
         *          will_reject_not_included: true
         *      }
         *      recite {
         *          #description of goods your are paying for#
         *      }
         *  }
         *
         *  payment_offer {
         *      amount: #######
         *      ark: ark2_npub
         *  }
         *
         *  payment_accept {
         *      transaction_key: XXXXXXX
         *  }
         *
         *  payment_recit {
         *      vtxo: [ #transactions# ]
         *  }
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         *
         */


    }

    @SneakyThrows
    private void mineAndWait() {
        ltbc.mine(1);
        Thread.sleep(1000);
    }

    public static void setWitness(TransactionInput ti, TransactionWitness witness) {
        Objects.requireNonNull(ti.getParentTransaction()).replaceInput(ti.getIndex(), ti.withWitness(witness));
    }


    private void fund(Transaction tx, Actor actor) {
        System.out.println("To be spent: " + actor.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getValue().equals(Coin.valueOf(0, 1))).count());

        // Add the funding input, post transaction signature
        TransactionOutput output = actor.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getValue().equals(Coin.valueOf(0, 1)) && transactionOutput.isAvailableForSpending()).findFirst().orElseThrow();
        TransactionInput fti1 = tx.addInput(output);
        output.markAsSpent(fti1);
        tx.replaceInput(fti1.getIndex(), actor.signSpendingInput(fti1));
//
//        ECKey key = arkService.kit.wallet().findKeyFromPubKeyHash(output.getScriptPubKey().getPubKeyHash(), Script.ScriptType.P2PKH);
//
//        // 2. The P2PKH scriptPubKey (the one you're spending from)
//        Script scriptPubKey = ScriptBuilder.createP2PKHOutputScript(key);
//
//        // 3. Sign the input
//        int inputIndex = fti1.getIndex();  // adjust if needed
//        Transaction.SigHash sigHash = Transaction.SigHash.ALL;
//        boolean anyoneCanPay = true;
//
//        // 4. Create the hash for signature
//        Sha256Hash sigHashBytes = tx.hashForSignature(inputIndex, scriptPubKey, sigHash, anyoneCanPay);
//
//        // 5. Create the ECDSA signature
//        ECKey.ECDSASignature signature = key.sign(sigHashBytes);
//        TransactionSignature txSig = new TransactionSignature(signature, sigHash, anyoneCanPay);
//
//        // 6. Create scriptSig (the unlocking script)
//        Script inputScript = ScriptBuilder.createInputScript(txSig, key);
//
//        fti1.setScriptSig(inputScript);

        // Sign the funding input
//        SendRequest sr = SendRequest.forTx(tx);
//        sr.ensureMinRequiredFee = false;
//        arkService.kit.wallet().signTransaction(sr);
    }

    private void send(Transaction tx, Actor arkService, Script rs, ArkUser alice) throws InterruptedException {
//        fund(tx, arkService);

        // Verify that the input is correct
//        tx.getInput(0).getScriptSig().correctlySpends(tx, 0, ScriptBuilder.createP2SHOutputScript(rs), Script.ALL_VERIFY_FLAGS);

        // Send it out
        System.out.println(tx);
        arkService.kit.peerGroup().broadcastTransaction(tx);

        // Mine
        Thread.sleep(5000);
        ltbc.mine(16);
        Thread.sleep(5000);

        // Check the balance
        System.out.println(alice.kit.wallet().getBalance());
    }
}
