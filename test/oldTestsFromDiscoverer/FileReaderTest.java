package oldTestsFromDiscoverer;

import discoverer.global.TextFileReader;
import static org.junit.Assert.*;
import java.util.*;

import org.junit.Test;

public class FileReaderTest {

    @Test
    public void test() {
        String[] s = TextFileReader.convert("/home/asch/tmp/ondra.txt");
        System.out.println(s);
    }

}
