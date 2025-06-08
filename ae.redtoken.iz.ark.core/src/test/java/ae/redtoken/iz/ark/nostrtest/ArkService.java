package ae.redtoken.iz.ark.nostrtest;

import org.bitcoinj.params.AbstractBitcoinNetParams;

class ArkService extends Actor {

    ArkService(AbstractBitcoinNetParams params) {
        super(params);

        kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
            System.out.println("Received coin " + coin + " to " + wallet);
        });

    }
}
