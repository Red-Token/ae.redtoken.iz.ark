package ae.redtoken.iz.ark.nostrtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bitcoinj.base.Sha256Hash;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

public class TestMessages {



    @Test
    void testMSH() {

        ObjectMapper om =  new ObjectMapper();

        SimpleModule module = new SimpleModule();
        module.addKeySerializer(Sha256Hash.class, new Sha256HashKeySerializer());
        module.addKeyDeserializer(Sha256Hash.class, new Sha256HashKeyDeserializer());
        module.addDeserializer(Sha256Hash.class, new AppTest2.Sha256HashDeserializer());
        module.addSerializer(Sha256Hash.class, new AppTest2.Sha256HashSerializer());
        om.registerModule(module);




    }
}
