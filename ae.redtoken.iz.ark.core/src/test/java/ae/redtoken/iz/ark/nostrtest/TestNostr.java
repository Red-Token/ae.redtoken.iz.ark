package ae.redtoken.iz.ark.nostrtest;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import nostr.api.EventNostr;
import nostr.api.NIP01;
import nostr.api.factory.EventFactory;
import nostr.base.PublicKey;
import nostr.event.BaseTag;
import nostr.event.Kind;
import nostr.event.impl.*;
import nostr.id.Identity;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class TestNostr {
    public final static Map<String, String> RELAYS = Map.of("strfry", "ws://127.0.0.1:7777");

    static class NIP0666Event extends GenericEvent {
        public NIP0666Event(PublicKey sender, Kind kind, List<BaseTag> tags, String content) {
            super(sender, kind, tags, content);
        }

        public NIP0666Event(GenericEvent event) {
            super(event.getPubKey(), event.getKind(), event.getTags(), event.getContent());
            this.setSignature(event.getSignature());
            this.setCreatedAt(event.getCreatedAt());
            this.setId(event.getId());
        }
    }

    static class NIP0666ArkQuotationEvent extends NIP0666Event {
        public NIP0666ArkQuotationEvent(@NonNull PublicKey pubKey, @NonNull List<BaseTag> tags, @NonNull String content) {
            super(pubKey, Kind.ARK_QUOTATION, tags, content);
        }

        public NIP0666ArkQuotationEvent(GenericEvent event) {
            super(event);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    static class NIP0666EventFactory extends EventFactory<NIP0666Event> {

        public NIP0666EventFactory(Identity sender, String content) {
            super(sender, content);
        }


        @Override
        public NIP0666Event create() {
            return new NIP0666Event(this.getSender(), Kind.ARK_REQUEST, this.getTags(), this.getContent());
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    static class NIP0666ArkQuotationEventFactory extends EventFactory<NIP0666Event> {

        public NIP0666ArkQuotationEventFactory(Identity sender, String content) {
            super(sender, content);
        }


        @Override
        public NIP0666ArkQuotationEvent create() {
            return new NIP0666ArkQuotationEvent(this.getSender(), this.getTags(), this.getContent());
        }
    }

    static class NIP0666<T extends NIP0666Event> extends EventNostr<T> {
        /**
         * Create a replaceable event
         *
         * @param content the content
         */
        public NIP0666<T> createReplaceableEvent(String content) {
            var event = new NIP0666EventFactory(this.getSender(), content).create();
            this.setEvent((T) event);
            return this;
        }
    }


    @Test
    void yelp() throws Exception {

        // Step 1: Generate a new identity (key pair)
//        Identity identity = Identity.generateRandomIdentity();
//        String privateKey = identity.getPrivateKey().toString();
//        String publicKey = identity.getPublicKey().toString();
//        System.out.println("Private Key: " + privateKey);
//        System.out.println("Public Key: " + publicKey);

//        // Step 2: Create a text note event
//        TextNoteEvent textNote = new TextNoteEvent(
//                identity.getPublicKey(), List.of(new EventTag("")),
//                "Hello, Nostr! This is a test post from nostr-java."
//        );
//
//        identity.sign(textNote);

        var sender = Identity.generateRandomIdentity();
        NIP01<TextNoteEvent> nip01 = new NIP01<>(sender);
//        GenericEvent send = nip01.createTextNoteEvent("Hello Nostr World!").setSender(sender).sign().send(RELAYS);

        NIP01<TextNoteEvent> note = nip01.createTextNoteEvent("SSSSSS");
        note.setSender(sender);
        note.sign();
        note.send(RELAYS);

        System.out.println("Note sent: " + note);

        Thread.sleep(5000);

        System.out.println("Note sent: " + note);

        Filters filters = Filters.builder().kinds(List.of(Kind.ARK_REQUEST)).build();

        String subId = "sub_" + sender.getPublicKey();

        EventCustomHandler2.handlers.put(subId, (event, message, relay) -> {

            NIP0666Event e = new NIP0666Event((GenericEvent) event);
            System.out.println(e);
        });

        nip01.setRelays(RELAYS).send(filters, subId);

        Thread.sleep(5000);
        System.out.println("Note sent: " + filters);

//        System.out.println(filters.getEvents().size());


        NIP0666<NIP0666Event> nip0666 = new NIP0666<>();
        nip0666.setSender(sender);

        NIP0666<NIP0666Event> replaceableEvent = nip0666.createReplaceableEvent("Oh solomio");
        replaceableEvent.setSender(sender);
        replaceableEvent.sign();
        replaceableEvent.send(RELAYS);

        Thread.sleep(5000);
        System.out.println("THE END!");

//        // Step 4: Connect to a relay and publish the event
//        String relayUrl = "wss://nostr-pub.wellorder.net";
//        Client client = Client.getInstance();
//        client.connect()
//        client.send(textNote); // Publish the signed event
//        System.out.println("Event published to relay: " + textNote.getId());

        /**
         *  Alice wants to buy pizza from Eve
         *
         *  Eve generates a bitcoin address
         *
         *  bitcoin://<onchainaddress>?amount=0.0001&label=<quid>&arkpp=<teahouse_npub>
         *
         */


        var alice = Identity.generateRandomIdentity();
        var eve = Identity.generateRandomIdentity();

        /**
         *  Step 1: Eve creates a quotation
         */

        ObjectMapper om = new ObjectMapper();
        ArkQuotationContent aqc = new ArkQuotationContent();

        aqc.amount = 30000;
        aqc.pubkey = "WHATEVER";
        aqc.arks.includeOnly = true;
        aqc.arks.include = new String[]{"myarc"};
        aqc.offer.currency = Currency.getInstance("USD");
        aqc.offer.items = new ArkOfferItems[]{
                new ArkOfferItems("Pepperoni", 1, 3.0)
        };
        aqc.offer.vat = "5%";

        String offer = om.writeValueAsString(aqc);
        System.out.println(offer);

        NIP0666<NIP0666ArkQuotationEvent> nip0666Stack = new NIP0666<>();
        nip0666Stack.setSender(eve);
        nip0666Stack.setRelays(RELAYS);
        NIP0666ArkQuotationEventFactory xy = new NIP0666ArkQuotationEventFactory(eve, offer);
        nip0666Stack.setEvent(xy.create());
        nip0666Stack.signAndSend();

        /**
         *  Step 2: Alice scans the bitcoin URL and fetches the offer
         */

        String id = nip0666Stack.getEvent().getId();

        NIP0666<NIP0666ArkQuotationEvent> aliceNip0666Stack = new NIP0666<>();
        aliceNip0666Stack.setRelays(RELAYS);
        aliceNip0666Stack.setSender(alice);
        GenericEvent ge = new GenericEvent();
        ge.setId(id);
        Filters filters2 = Filters.builder().events(List.of(ge)).kinds(List.of(Kind.ARK_QUOTATION)).build();
        String subId2 = "sub_" + alice.getPublicKey();

        BlockingQueue<GenericEvent> queue = new ArrayBlockingQueue<>(1);

        EventCustomHandler2.handlers.put(subId2, (event, message, relay) -> {
            queue.add((GenericEvent) event);
        });

        aliceNip0666Stack.send(filters2, subId2);

        GenericEvent take = queue.take();
        System.out.println(take);
    }

    public static class ArkQuotationContent {
        public String pubkey;
        public int amount;
        public ArkList arks = new ArkList();
        public ArkOffer offer = new ArkOffer();

        public ArkQuotationContent() {
        }
    }

    public static class ArkList {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String[] include;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String[] exclude;
        public boolean includeOnly = false;

        public ArkList() {
        }
    }

    public static class ArkOffer {
        private Currency currency;

        @JsonGetter("currency")
        public String getCurrencyCode() {
            return currency.getCurrencyCode();
        }

        @JsonSetter("currency")
        public void setCurrencyCode(String code) {
            this.currency = Currency.getInstance(code);
        }

        public ArkOfferItems[] items;
        public String vat;
    }

    public static class ArkOfferItems {
        public String description;
        public double quantity;
        public double price;

        public ArkOfferItems() {
        }

        public ArkOfferItems(String description, double quantity, double price) {
            this.description = description;
            this.quantity = quantity;
            this.price = price;
        }
    }

    @Test
    void testCrateJson() throws JsonProcessingException {

        ObjectMapper om = new ObjectMapper();
        ArkQuotationContent aqc = new ArkQuotationContent();

        aqc.amount = 30000;
        aqc.pubkey = "WHATEVER";
        aqc.arks.includeOnly = true;
        aqc.arks.include = new String[]{"myarc"};
        aqc.offer.currency = Currency.getInstance("USD");
        aqc.offer.items = new ArkOfferItems[]{
                new ArkOfferItems("Pepperoni", 1, 3.0)
        };
        aqc.offer.vat = "5%";

        String x = om.writeValueAsString(aqc);
        System.out.println(x);
    }
}
