package ae.redtoken.iz.ark.nostrtest;

import lombok.NonNull;
import nostr.base.Command;
import nostr.base.Relay;
import nostr.command.CommandHandler;
import nostr.context.CommandContext;
import nostr.context.impl.DefaultCommandContext;
import nostr.event.BaseMessage;

import java.util.logging.Logger;

public abstract class BaseCustomCommandHandler implements CommandHandler {

    private final Command command;
    protected static final Logger log = Logger.getLogger(BaseCustomCommandHandler.class.getName());

    public BaseCustomCommandHandler(@NonNull Command command) {
        this.command = command;
    }

    @Override
    public void handle(CommandContext commandContext) {

        DefaultCommandContext context = (DefaultCommandContext) commandContext;

        var relay = context.getRelay();
        var message = context.getMessage();

        assert command.toString().equalsIgnoreCase(message.getCommand());

        onCommand(message, relay);
    }

    protected abstract void onCommand(@NonNull BaseMessage message, @NonNull Relay relay);

}