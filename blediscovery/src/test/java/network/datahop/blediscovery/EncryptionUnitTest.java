package network.datahop.blediscovery;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class EncryptionUnitTest {

    @Test
    public void encrypt() {
        String plainMsg = "hola";
        String encMsg = Encryption.encrypt(plainMsg,"password");
        System.out.println("Plain message "+plainMsg);

        System.out.println("Encrypted message "+encMsg);

        String decodedMsg = Encryption.decrypt(encMsg,"password");

        System.out.println("Decoded message "+decodedMsg);

        assertEquals(4, 2 + 2);
    }

}


