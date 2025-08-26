package ae.redtoken.iz.ark.nostrtest;

//import ae.redtoken.iz.ark.nostrtest.Actor;

import org.bitcoinj.core.NetworkParameters;

public class ArkService extends Actor {

    public ArkService(NetworkParameters params) {
        super(params);

        kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
            System.out.println("Received coin " + coin + " to " + wallet);
        });

    }
}
