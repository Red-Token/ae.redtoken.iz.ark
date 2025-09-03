package ae.redtoken.iz.ark.nostrtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

public class TestMessages {

    public static TransactionOutPoint fromString(String string) {
        String[] components = string.split(":");
        return new TransactionOutPoint(Long.parseLong(components[1]), Sha256Hash.wrap(components[0]));
    }

    @Test
    void testMSH() {

//        ObjectMapper om =  new ObjectMapper();
//
//        SimpleModule module = new SimpleModule();
//        module.addKeySerializer(Sha256Hash.class, new Sha256HashKeySerializer());
//        module.addKeyDeserializer(Sha256Hash.class, new Sha256HashKeyDeserializer());
//        module.addDeserializer(Sha256Hash.class, new AppTest2.Sha256HashDeserializer());
//        module.addSerializer(Sha256Hash.class, new AppTest2.Sha256HashSerializer());
//        om.registerModule(module);

        TransactionOutPoint top = new TransactionOutPoint(0, Sha256Hash.of("Hello".getBytes()));

        String string = top.toString();

        System.out.println(string);

        System.out.println(fromString(string).toString());
    }
}
