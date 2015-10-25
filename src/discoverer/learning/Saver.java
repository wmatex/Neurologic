package discoverer.learning;

import discoverer.construction.network.rules.KappaRule;
import discoverer.construction.network.Kappa;
import discoverer.construction.network.Lambda;
import discoverer.construction.network.rules.SubK;
import discoverer.construction.network.KL;
import discoverer.construction.network.LiftedNetwork;
import discoverer.global.Tuple;
import discoverer.global.Glogger;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JFrame;

/**
 * Saver for the best learned network so far
 */
public class Saver {

    private static Set<Tuple<KappaRule, Double>> rules = new HashSet<Tuple<KappaRule, Double>>();
    private static Set<Tuple<Kappa, Double>> offsets = new HashSet<Tuple<Kappa, Double>>();
    private static Double learnError, threshold, dispersion;

    public static void save(LiftedNetwork net, double le, double th, double disp) {
        rules.clear();
        offsets.clear();
        learnError = le;
        threshold = th;
        dispersion = disp;

        for (Kappa k : net.getKappas()) {
            if (!k.isElement()) {
                offsets.add(new Tuple<>(k, k.getOffset()));
                Glogger.debug(k + " -> " + k.getOffset());

                for (KappaRule kr : k.getRules()) {
                    Tuple<KappaRule, Double> t = new Tuple<>(kr, kr.getWeight());
                    Glogger.debug(kr + " -> " + kr.getWeight());
                    rules.add(t);
                }
            }
        }
    }

    public static void save2(LiftedNetwork net, double le, double th, double disp) {
        KL network = net.last;  //this recursion could be shortcutted too!

        rules.clear();
        offsets.clear();
        learnError = le;
        threshold = th;
        dispersion = disp;
        if (network instanceof Kappa) {
            save((Kappa) network);
        } else {
            save((Lambda) network);
        }
    }

    private static void save(Kappa k) {
        if (k.isElement()) {
            return;
        }

        offsets.add(new Tuple<>(k, k.getOffset()));
        Glogger.debug(k + " -> " + k.getOffset());
        for (KappaRule kr : k.getRules()) {
            Tuple<KappaRule, Double> t = new Tuple<>(kr, kr.getWeight());
            Glogger.debug(kr + " -> " + kr.getWeight());
            rules.add(t);
            save(kr.getBody().getParent());
        }
    }

    private static void save(Lambda l) {
        for (SubK sk : l.getRule().getBody()) {
            save(sk.getParent());
        }
    }

    public static void load() {
        for (Tuple<KappaRule, Double> t : rules) {
            Glogger.debug(t.x + " : " + t.x.getWeight() + " -> " + t.y);
            t.x.setWeight(t.y);
        }

        for (Tuple<Kappa, Double> t : offsets) {
            Glogger.debug(t.x + " : " + t.x.getOffset() + " -> " + t.y);
            t.x.setOffset(t.y);
        }

        Glogger.process("Loading: trainError: " + learnError + ", threshold: " + threshold + ", dispersion" + dispersion);
        learnError = null;
        threshold = null;
        dispersion = null;
    }

    public static boolean isBetterThenBest(double le, double th, double disp) {
        Glogger.process("best error so far = " + learnError);
        if (learnError == null) {
            return true;
        }
        if (learnError > le) {
            return true;
        }
        return false;
        /*
         *if (learnError > le) return true;
         *if (learnError < le) return false;
         *if (dispersion > disp) return false;
         *if (dispersion < disp) return true;
         *if (Math.abs(threshold - 0.5) <= Math.abs(th - 0.5)) return false;
         *return true;
         */
    }
}
