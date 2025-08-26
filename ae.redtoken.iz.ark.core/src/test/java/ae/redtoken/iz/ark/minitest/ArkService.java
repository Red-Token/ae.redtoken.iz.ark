package ae.redtoken.iz.ark.minitest;

//import ae.redtoken.iz.ark.nostrtest.Actor;
import org.bitcoinj.params.AbstractBitcoinNetParams;

public class ArkService extends Actor {

    public ArkService(AbstractBitcoinNetParams params) {
        super(params);

        kit.wallet().addCoinsReceivedEventListener((wallet, transaction, coin, coin1) -> {
            System.out.println("Received coin " + coin + " to " + wallet);
        });

    }
}
