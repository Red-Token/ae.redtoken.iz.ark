package ae.redtoken.iz.ark.nostrtest;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.bitcoinj.base.Sha256Hash;

import java.io.IOException;

// Key serializer
public class Sha256HashKeySerializer extends StdSerializer<Sha256Hash> {
    public Sha256HashKeySerializer() {
        super(Sha256Hash.class);
    }

    @Override
    public void serialize(Sha256Hash value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeFieldName(value.toString()); // convert to string
    }
}
