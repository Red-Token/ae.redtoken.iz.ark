package ae.redtoken.iz.ark.nostrtest;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import org.bitcoinj.base.Sha256Hash;

import java.io.IOException;

// Key deserializer
public class Sha256HashKeyDeserializer extends KeyDeserializer {
    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        return Sha256Hash.wrap(key);
    }
}
