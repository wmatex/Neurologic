package discoverer.global;

import java.util.*;
import java.io.*;

/**
 * Ugly java6 filetostring
 */
public class FileToStringListJava6 {

    /**
     * converts a given string into a string array line by line
     *
     * @param p
     * @param maxLineLength
     * @return
     */
    public static String[] convert(String p, int maxline) {
        List<String> lines = new ArrayList<String>();
        BufferedReader buffReader = null;
        try {
            buffReader = new BufferedReader(new FileReader(p));
            String line = null;
            while ((line = buffReader.readLine()) != null) {
                if (line.length() != 0 && line.length() < maxline) {
                    lines.add(line);
                }
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                buffReader.close();
            } catch (IOException ioe1) {
                //Leave It
            }
        }

        return ListToArray(lines);
    }

    /*
     converts a given list into array
     */
    private static String[] ListToArray(List<String> list) {
        String[] ret = new String[list.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = list.get(i);
        }

        return ret;
    }
}
