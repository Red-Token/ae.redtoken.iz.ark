package ae.redtoken.iz.ark.nostrtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import nostr.event.Kind;
import nostr.event.impl.Filters;
import nostr.event.impl.GenericEvent;
import org.bitcoin.tfw.ltbc.tc.LTBCMainTestCase;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.*;
import org.bitcoinj.wallet.SendRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

import static ae.redtoken.iz.ark.nostrtest.TestNostr.RELAYS;


/**
 *
 */
public class AppTest2 extends LTBCMainTestCase {
    record ArkRoundInitiate(Coin minValue) {
    }

    record ArkOnboardingAsset(TransactionOutput output) {
    }

    record ArkOnboardingRequest(byte[] key, List<ArkOnboardingAsset> assets) {
    }

    record ArkRoundVTXTreeProposal(ArkTree tree) {
    }

    record NewVTXTreeAccept(Map<Sha256Hash, byte[]> signedStack) {
    }

    record StartConfirmationRequest(byte[] rootTx, Map<Sha256Hash, byte[]> arkServiceSignatures) {
    }

    record StartAccept(Map<TransactionOutPoint, byte[]> witnessMap) {
    }

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
            addFee(tx, arkService);

            tree.locks.put(Sha256Hash.of(rs), rs);

            return new Zel(tree, nodeOutput, rs);
        }

        record SubNode(List<FoundingMember> list, TransactionOutput output) {
        }

        void createArkTree(ArkTree tree, List<ArkOnboardingRequest> requests) {
            List<FoundingMember> members = requests.stream().map(arkOnboardingRequest ->
                    new FoundingMember(
                            arkOnboardingRequest.assets.stream().findFirst().orElseThrow().output.getValue(),
                            arkOnboardingRequest.key)).toList();

//            List<TransactionOutput> fundingOutputs

            Transaction rootTx = new Transaction();
            rootTx.setVersion(2);

            Coin value = members.stream().map(FoundingMember::value).reduce(Coin.ZERO, Coin::add);

            // Create a VTXO based on userKeys
            Script rs = asf.createVTXONodeScript(members.stream().map(m -> m.key).toArray(byte[][]::new));
            tree.locks.put(Sha256Hash.of(rs.program()), rs.program());

            // Here we should fund the ARK from the user
            // Create the output and send in the hash of the script into that output.
            TransactionOutput rootTo = rootTx.addOutput(value, ScriptBuilder.createP2WSHOutputScript(Sha256Hash.hash(rs.program())));

            for (ArkOnboardingRequest request : requests) {
                ArkOnboardingAsset arkOnboardingAsset = request.assets.stream().findFirst().orElseThrow();
                TransactionInput rootTi = rootTx.addInput(arkOnboardingAsset.output);

                // TODO here we mark the output as spent
                arkOnboardingAsset.output.markAsSpent(rootTi);
            }

            // Add a feeInput to transaction.
            addFee(rootTx, arkService);

            tree.roots = Collections.singletonList(rootTx.getTxId());
            tree.nodes.put(rootTx.getTxId(), rootTx);

            createArkTree(tree, members, rootTo);
        }

        void createArkTree(ArkTree tree, List<FoundingMember> members, TransactionOutput output) {

            // rec create the tree
            Transaction branchTx = new Transaction();
            branchTx.setVersion(2);

            TransactionInput branchTi = branchTx.addInput(output);


//            output.markAsSpent(branchTi);

            Collection<SubNode> subNodes = new ArrayList<>();
            Collection<TransactionOutput> leafs = new ArrayList<>();

            int subMembersSize = members.size() / 2;

            for (int i = 0; i < members.size(); i += subMembersSize) {
                // TODO: this will fail spectacluary for size like 3
                List<FoundingMember> list = members.subList(i, i + subMembersSize);

                if (list.size() == 1) {
                    FoundingMember member = list.getFirst();

                    Script rs = asf.createVTXOLeafScript(member.key);
                    tree.locks.put(Sha256Hash.of(rs.program()), rs.program());
                    TransactionOutput leafOutput = branchTx.addOutput(
                            member.value,
                            ScriptBuilder.createP2WSHOutputScript(rs));

                    leafs.add(leafOutput);

                } else {

                    Script rs = asf.createVTXONodeScript(list.stream().map(m -> m.key).toArray(byte[][]::new));
                    tree.locks.put(Sha256Hash.of(rs.program()), rs.program());
                    TransactionOutput nodeOutput = branchTx.addOutput(
                            Coin.valueOf(list.stream().mapToLong(m -> m.value.value).sum()),
                            ScriptBuilder.createP2WSHOutputScript(rs));

                    subNodes.add(new SubNode(list, nodeOutput));
                }
            }

            addFee(branchTx, arkService);
            tree.nodes.put(branchTx);
            tree.spendPath.put(output.getOutPointFor(), new ArkTree.TransactionInPoint(branchTx.getTxId(), branchTi.getIndex()));


            // Now that the transaction is finished we add the leafs
            leafs.forEach(leaf ->
                    tree.leafs.add(new ArkTree.ArkLeaf(leaf.getOutPointFor())));

            subNodes.forEach(subNode ->
                    createArkTree(tree, subNode.list, subNode.output));
        }

        private void addFee(Transaction tx, Actor arkService) {
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

    static class ArkInitiator extends ArkUser {
        private final ArkService service;

        //Hack
        ArkOnboardingRequest aor;
        List<ArkOnboardingAsset> assets;

        //Hack2
//        Map<ByteBuffer, NewVTXTreeAccept> nvtaMap = new HashMap<>();
        NewVTXTreeAccept accept;
        Map<Sha256Hash, ArkVirtualTransactionNode> avntMap = Maps.newHashMap();


        public ArkInitiator(NetworkParameters params, ArkService service) {
            super(params);
            this.service = service;
        }

        void on(ArkRoundInitiate air) {
            this.aor = new ArkOnboardingRequest(
                    getActivePublicKey(), assets);
        }

        void on(ArkRoundVTXTreeProposal proposal) {
            ArkTree tree = proposal.tree;

            ///  Each user goes over the proposal
            for (ArkTree.ArkLeaf leaf : tree.leafs) {
                byte[] pubKey = asf.extractUserHashesFromVTXO(tree.getProgram(leaf.outPoint))[0];

                if (!Arrays.equals(pubKey, getActivePublicKey()))
                    continue;

                List<ArkVirtualTransactionNode> list = new ArrayList<>();

                Transaction transaction;
                for (TransactionOutPoint outPoint = leaf.outPoint;
                     !tree.roots.contains(outPoint.hash());
                     outPoint = transaction.getInput(0).getOutpoint()) {

                    //The branch transaction
                    transaction = tree.nodes.get(outPoint.hash());
                    TransactionOutPoint branchOutPoint = transaction.getInput(0).getOutpoint();
                    TransactionOutput output = tree.nodes.get(branchOutPoint.hash()).getOutput(branchOutPoint.index());

                    byte[] lock = tree.locks.get(
                            Sha256Hash.wrap(Objects.requireNonNull(
                                    Script.parse(output.getScriptBytes()).chunks().get(1).data)));

                    ArkVirtualTransactionNode avtn = new ArkVirtualTransactionNode(transaction.serialize(), lock);
                    list.addFirst(avtn);
                    avntMap.put(transaction.getTxId(), avtn);
                }

                byte[] rootBytes = tree.nodes.get(tree.roots.stream().findFirst().orElseThrow()).serialize();
                ArkVirtualTransactionStack avts = new ArkVirtualTransactionStack(rootBytes, list);

                Map<Sha256Hash, byte[]> signedStack = signStack(avts);

                // The response

                accept = new NewVTXTreeAccept(signedStack);
//                nvtaMap.put(ByteBuffer.wrap(getActivePublicKey()), accept);
            }
        }

        StartAccept sa;

        public void on(StartConfirmationRequest scr) {
            Transaction rootTx = Transaction.read(ByteBuffer.wrap(scr.rootTx));
            Map<TransactionOutPoint, byte[]> witnessMap = Maps.newHashMap();

            //TODO: bit of a hack I guess we could also keept track of this
            for (ArkOnboardingAsset asset : assets) {
                TransactionOutPoint outPoint = asset.output.getOutPointFor();
                TransactionInput input = rootTx.getInputs().stream().filter(transactionInput -> transactionInput.getOutpoint().equals(outPoint)).findFirst().orElseThrow();
                byte[] witnessBytes = createP2WPKHWitness(scr.rootTx, input.getIndex(), asset.output().getValue());
                witnessMap.put(input.getOutpoint(), witnessBytes);
            }

            sa = new StartAccept(witnessMap);
        }
    }

    static class ArkTree {
        record TransactionInPoint(Sha256Hash txId, long index) {
        }

        public ArkService arkService;
        public Map<TransactionOutPoint, TransactionInPoint> spendPath = new HashMap<>();

        static class NodeMap extends HashMap<Sha256Hash, Transaction> {
            Transaction put(Transaction transaction) {
                return put(transaction.getTxId(), transaction);
            }
        }

        record ArkLeaf(TransactionOutPoint outPoint) {
        }

        Collection<Sha256Hash> roots;
        NodeMap nodes = new NodeMap();
        Map<Sha256Hash, byte[]> locks = new HashMap<>();
        Collection<ArkLeaf> leafs = new ArrayList<>();

        TransactionOutput getOutput(TransactionOutPoint top) {
            return nodes.get(top.hash()).getOutput(top.index());
        }

        byte[] getProgram(TransactionOutPoint top) {
            byte[] hash = ScriptPattern.extractHashFromP2SH(Script.parse(getOutput(top).getScriptBytes()));
            return locks.get(Sha256Hash.wrap(hash));
        }
    }

    //    static void assignWitness(TransactionInput ti, ArkTree tree, ArkScriptFactory asf, Map<ByteBuffer, Map<Sha256Hash, byte[]>> signedStackMap, Map<Sha256Hash, byte[]> arkServiceSignatures) {
    static void assignWitness(TransactionInput ti, ArkTree tree, ArkScriptFactory asf, Map<ByteBuffer, NewVTXTreeAccept> acceptMap, StartConfirmationRequest scr) {

        // first we select the output
        Script outputScript = Script.parse(Objects.requireNonNull(ti.getConnectedOutput()).getScriptBytes());

        // from here we get the program hash
        Sha256Hash programHash = Sha256Hash.wrap(ScriptPattern.extractHashFromP2SH(outputScript));

        // get the program from the locks
        byte[] program = tree.locks.get(programHash);

        // decode the program to get the pubkeys needed
        byte[][] userKeys = asf.extractUserHashesFromVTXO(program);

        // create the list of user signatures
        List<byte[]> userSignatures = Lists.newArrayList();

        for (byte[] userKey : userKeys) {
            userSignatures.addFirst(acceptMap.get(ByteBuffer.wrap(userKey)).signedStack.get(programHash));
        }

        byte[][] userSigs = userSignatures.toArray(new byte[userSignatures.size()][]);

        // create the witness
        TransactionWitness witness = ArkScriptFactory.createVTXONodeUnlockWitnessScript(
                userSigs,
                scr.arkServiceSignatures.get(programHash),
                program);

        // assign it to the input
        setWitness(ti, witness);
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

//        onTheWire = new OnTheWire(params);

        ArkService arkService = new ArkService(params);
        ArkInitiator alice = new ArkInitiator(params, arkService);
        ArkInitiator bob = new ArkInitiator(params, arkService);
        ArkInitiator carol = new ArkInitiator(params, arkService);
        ArkInitiator david = new ArkInitiator(params, arkService);

        ArkUser eve = new ArkUser(params);
        ArkUser freddy = new ArkUser(params);

        ArkUser[] users = Arrays.asList(alice, bob, carol, david, eve, freddy).toArray(new ArkUser[0]);

        final double coinsToSendToArkService = 20;
        final double coinsToSendToUsers = 2;

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

        Arrays.stream(users).forEach(user -> user.asf = asf);

        // Start the ARK
        /**
         *  ARK RoundInitiate
         *      - Inform that a new ARK is starting and the minimumEntry to join
         *
         *  ARK OnboardingRequest
         *      - Send in your start key and the Inputs to join with
         *
         *  ARK NewVTXTreeProposal
         *      - Send out the tree for review
         *
         *  ARK NewVTXTreeAccept
         *      - Accept the tree (by sign the nodes)
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
            record Initiator(ArkInitiator user) {
            }

            List<Initiator> initiators = new ArrayList<>();

            ArkRoundFactory arf = new ArkRoundFactory(params, asf, arkService);

            // Let's fund the founding output
            {
                Transaction arkFundingTx = new Transaction();
                arkFundingTx.setVersion(2);

                // This is the output that is used to FUND the rootNode (the fundingInput in the rootNode takes its capital from here
                for (ArkInitiator initiator : List.of(alice, bob, carol, david)) {
                    Script arkFundingLockScript = ScriptBuilder.createP2WPKHOutputScript(initiator.activeKey);
                    initiator.assets = List.of(new ArkOnboardingAsset(arkFundingTx.addOutput(Coin.valueOf(1, 0), arkFundingLockScript)));
                    initiators.add(new Initiator(initiator));
                }

                SendRequest sr = SendRequest.forTx(arkFundingTx);
                sr.feePerKb = Coin.valueOf(1000);
                arkService.kit.wallet().completeTx(sr);

                // Send it out
                sendAndVerify(sr.tx, arkService, alice);
            }

            /// START
            ArkRoundInitiate ari = new ArkRoundInitiate(Coin.valueOf(0, 10));

            for (Initiator initiator : initiators) {
                initiator.user.on(ari);
            }

            /// The users ask to onboard the ARK
            List<ArkOnboardingRequest> aors = Lists.newArrayList();

            for (Initiator initiator : initiators) {
                aors.add(initiator.user.aor);
            }

            /// Service create the tree
            ArkTree tree = new ArkTree();
            tree.arkService = arkService;

            // Now we make the next node.
            arf.createArkTree(tree, aors);

            /// Send it out for a review
            ArkRoundVTXTreeProposal proposal = new ArkRoundVTXTreeProposal(tree);

            Map<Sha256Hash, ArkVirtualTransactionNode> avntMap = Maps.newHashMap();
            Map<ByteBuffer, NewVTXTreeAccept> nvtaMap = new HashMap<>();

            for (Initiator initiator : initiators) {
                initiator.user.avntMap = avntMap;
                initiator.user.on(proposal);
                nvtaMap.put(ByteBuffer.wrap(initiator.user.getActivePublicKey()), initiator.user().accept);
            }

            Transaction rootTx = tree.nodes.get(tree.roots.stream().findFirst().orElseThrow());

            // Service provides branch
            ArkVirtualTransactionStack vtxs_full = new ArkVirtualTransactionStack(rootTx.serialize(), avntMap.values());

            // S signs the tree
            // This is a map of the hash of the program (stored in the output) and the signature
            StartConfirmationRequest scr = new StartConfirmationRequest(rootTx.serialize(), arkService.signStack(vtxs_full));

            for (Transaction node : tree.nodes.values()) {
                // Filter out the root node
                if (tree.roots.contains(node.getTxId()))
                    continue;

                for (int i = 0; i < node.getInputs().size() - 1; i++) {
                    TransactionInput input = node.getInput(i);
                    assignWitness(input, tree, asf, nvtaMap, scr);
                }
            }

            /// Sign the root
            // Now let's complete and fund this transaction
            // Todo: this part here needs to be rewritten to work with the signing strategy


            for (Initiator initiator : initiators) {
                // Create the witness
                // TODO move this to the script factory
                initiator.user.on(scr);

                // Go over the response and update the witness
                for (TransactionOutPoint top : initiator.user.sa.witnessMap.keySet()) {
                    TransactionInput ti = rootTx.getInputs().stream().filter(input -> input.getOutpoint().equals(top)).findFirst().orElseThrow();
                    setWitness(ti, TransactionWitness.read(ByteBuffer.wrap(initiator.user.sa.witnessMap.get(top))));
                }
            }


            /// Send it out
            sendAndVerify(rootTx, arkService, alice);

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
            // this is a hashmap of the txid and the corresponding transaction
            Transaction vtx1_1_1 = new Transaction();
            vtx1_1_1.setVersion(2);

            // Create the output and send in the hash of the script into that output.
            vtx1_1_1.addOutput(Coin.valueOf(0, 66), alice.kit.wallet().freshReceiveAddress());

            TransactionOutput output = alice.unspentVTXOs.stream().findFirst().orElseThrow();

            // Connect the input
            TransactionInput ti_1_1_1 = vtx1_1_1.addInput(output);
            fund(vtx1_1_1, arkService);

            // Lets rool!
            byte[] rs1_1_1 = tree.locks.get(Sha256Hash.of(asf.createVTXOLeafScript(alice.getActivePublicKey()).program()));
            byte[] sigABin = alice.signInputWitness(vtx1_1_1.serialize(), rs1_1_1, ti_1_1_1.getIndex(), Objects.requireNonNull(ti_1_1_1.getConnectedOutput()).getValue());

            // Collaborative exit request
            // Set of UTXO:s to exit, OutPoint
            // This is on the ARK Service side

            Map<Integer, SignatureRequest> programMap = Map.of(ti_1_1_1.getIndex(), new SignatureRequest(rs1_1_1, sigABin));

            Transaction tx = Transaction.read(ByteBuffer.wrap(vtx1_1_1.serialize()));
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
            ArkTree.TransactionInPoint transactionInPoint = tree.spendPath.get(rootTx.getOutput(0).getOutPointFor());
            Transaction vtx1 = tree.nodes.get(transactionInPoint.txId);
            Transaction vtx1_1 = tree.nodes.get(tree.spendPath.get(vtx1.getOutput(0).getOutPointFor()).txId);

            sendAndVerify(vtx1, arkService, alice);
            sendAndVerify(vtx1_1, arkService, alice);
            sendAndVerify(vtx1_1_1, arkService, alice);

            System.out.println("HLLSLSSL");
            Assertions.assertEquals(266000000, alice.kit.wallet().getBalance().value);
        }

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
        //TODO Why 16 blocks?
        ltbc.mine(16);
    }

    public static void setWitness(TransactionInput ti, TransactionWitness witness) {
        TransactionInput input = ti.withWitness(witness);
        Objects.requireNonNull(ti.getParentTransaction()).replaceInput(ti.getIndex(), input);
    }


    private void fund(Transaction tx, Actor actor) {
        System.out.println("To be spent: " + actor.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getValue().equals(Coin.valueOf(0, 1))).count());

        // Add the funding input, post transaction signature
        TransactionOutput output = actor.kit.wallet().getUnspents().stream().filter(transactionOutput -> transactionOutput.getValue().equals(Coin.valueOf(0, 1)) && transactionOutput.isAvailableForSpending()).findFirst().orElseThrow();
        TransactionInput fti1 = tx.addInput(output);
        output.markAsSpent(fti1);
        tx.replaceInput(fti1.getIndex(), actor.signSpendingInput(fti1));
    }

    static class TransactionVerifingNewBestBlockListener implements NewBestBlockListener {

        final WalletAppKit kit;
        final Sha256Hash txId;
        final BlockingQueue<String> q = new ArrayBlockingQueue<>(1);

        TransactionVerifingNewBestBlockListener(WalletAppKit kit, Sha256Hash txId) {
            this.kit = kit;
            this.txId = txId;
        }

        @SneakyThrows
        @Override
        public void notifyNewBestBlock(StoredBlock storedBlock) throws VerificationException {
            System.out.println("New Block: " + storedBlock.getHeader().getHash());
            System.out.println("Looking for: " + this.txId);
            System.out.println("so high: " + storedBlock.getHeight());

            Block block = kit.peerGroup().getConnectedPeers().stream()
                    .findFirst().orElseThrow().getBlock(storedBlock.getHeader().getHash()).get();

            for (Transaction t : Objects.requireNonNull(block.getTransactions())) {
                System.out.println("Transaction: " + t);

                if (txId.equals(t.getTxId())) {
                    System.out.println("Found Transaction: " + t.getTxId());
                    System.out.println("Signing off");
                    kit.chain().removeNewBestBlockListener(this);
                    q.add("YES");
                }
            }
        }
    }

    @SneakyThrows
    private void sendAndVerify(Transaction tx, Actor arkService, ArkUser user) throws InterruptedException {
        TransactionVerifingNewBestBlockListener bbl = new TransactionVerifingNewBestBlockListener(user.kit, tx.getTxId());
        user.kit.chain().addNewBestBlockListener(bbl);

        // Send it out
//        arkService.kit.peerGroup().broadcastTransaction(tx).broadcastAndAwaitRelay().get();
        arkService.kit.peerGroup().broadcastTransaction(tx).awaitSent().get();

        mineAndWait();
        // Check the balance
        System.out.println(user.kit.wallet().getBalance());
        System.out.println(bbl.q.take());
    }
}
