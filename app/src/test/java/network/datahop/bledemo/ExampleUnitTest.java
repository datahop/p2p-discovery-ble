package network.datahop.bledemo;

import org.junit.Test;

import static org.junit.Assert.*;

import network.datahop.blediscovery.Encryption;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

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