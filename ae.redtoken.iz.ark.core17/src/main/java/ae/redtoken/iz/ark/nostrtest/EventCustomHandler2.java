package ae.redtoken.iz.ark.nostrtest;

import lombok.NonNull;
import nostr.base.IEvent;
import nostr.base.Relay;
import nostr.base.annotation.CustomHandler;
import nostr.event.BaseMessage;
import nostr.event.message.EventMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import static nostr.base.Command.EVENT;

@CustomHandler(command = EVENT)
public class EventCustomHandler2 extends BaseCustomCommandHandler {

    public interface FluidEventHandler {
        void onEvent(IEvent event, BaseMessage message, Relay relay);
    }


    public static Map<String, FluidEventHandler> handlers = new HashMap<>();

    public EventCustomHandler2() {
        super(EVENT);
    }

    /**
     * Log the event received from the relay
     *
     * @param message the message
     * @param relay   the relay
     */
    protected void onCommand(@NonNull BaseMessage message, @NonNull Relay relay) {
        var eventMessage = (EventMessage) message;
        var event = eventMessage.getEvent();
        var subId = eventMessage.getSubscriptionId();

        if(handlers.containsKey(subId)) {
            handlers.get(subId).onEvent(event, message, relay);
        }

        if (subId == null) {
            log.log(Level.INFO, ">>> ZOOL23 Sending event {0} to relay {1}", new Object[]{event, relay});
        } else {
            log.log(Level.INFO, "<<< ZOOL232323 Received EVENT {0} from relay {1} with subscription id {2}", new Object[]{event, relay, subId});
        }
    }

}
