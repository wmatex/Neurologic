/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package discoverer.grounding;

import discoverer.construction.ConstantFactory;
import discoverer.construction.Parser;
import discoverer.construction.TemplateFactory;
import discoverer.construction.Variable;
import discoverer.construction.example.Example;
import discoverer.construction.template.KL;
import discoverer.construction.template.Kappa;
import discoverer.construction.template.Lambda;
import discoverer.construction.template.rules.KappaRule;
import discoverer.construction.template.rules.Rule;
import discoverer.construction.template.rules.SubK;
import discoverer.construction.template.rules.SubKL;
import discoverer.construction.template.rules.SubL;
import discoverer.construction.template.specialPredicates.SimilarityPredicate;
import discoverer.global.Glogger;
import discoverer.global.Tuple;
import discoverer.grounding.evaluation.GroundedTemplate;
import discoverer.grounding.network.GroundKL;
import discoverer.grounding.network.GroundKappa;
import discoverer.grounding.network.GroundLambda;
import ida.ilp.logic.Clause;
import ida.ilp.logic.Literal;
import ida.ilp.logic.LogicUtils;
import ida.ilp.logic.Term;
import ida.ilp.logic.io.PrologParser;
import ida.ilp.logic.subsumption.Matching;
import ida.utils.tuples.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import supertweety.lrnn.grounder.BottomUpGrounder;

/**
 *
 * @author Gusta
 */
public class BottomUpConnector extends Grounder {

    Map<Literal, GroundKL> herbrandModel;
    Map<Rule, List<List<Literal>>> groundRuleMap;
    Map<Literal, Map<Rule, List<List<Literal>>>> head2Tails;

    boolean caching = true;
    HashMap<Literal, GroundKL> cache;
    HashSet<Literal> openAtomSet;
    int recursiveLoopCount;
    private LinkedHashMap<Literal, Integer> recursiveLoops;

    Example facts;

    String[] extraRules = new String[]{"similar(X,X)"};

    public static void main(String[] args) {
        String[] rules = new String[]{
            "0.96875 holdsK(S,P,O) :- holdsL1(S,P,O)",
            "1.0 similarK1s(A,B) :- similar(A,B)",
            "1.0 similarK1p(A,B) :- similar(A,B)",
            "1.0 similarK1o(A,B) :- similar(A,B)",
            "holdsL1(S,P,O) :- similarK1s(S,concept:mammal:tiger),similarK1p(P,concept:animalistypeofanimal),similarK1o(O,concept:mammal:cats)",
            "similar(X,X)"
        };
        String[] facts = new String[]{"nic(dummy)"};
        Set<String> allConstants = new HashSet<>();
        allConstants.add("concept:mammal:tiger");
        allConstants.add("concept:animalistypeofanimal");
        allConstants.add("concept:mammal:cats");
        /*
        Set<Literal> herbrandModel = getHerbrandModel(rules, facts, allConstants);
        for (Literal literal : herbrandModel) {
            System.out.println(literal);
        }
         */    }

    public GroundKL getGroundLRNN(List<Rule> rules, Example ifacts, String query) {
        cache = new HashMap<>();
        openAtomSet = new HashSet<>();
        recursiveLoopCount = 0;
        recursiveLoops = new LinkedHashMap<>();

        this.facts = ifacts;

        if (groundRuleMap == null) {
            head2Tails = new HashMap<>();
            String substring = null;
            if (ifacts != null) {
                substring = facts.hash.substring(0, facts.hash.lastIndexOf("."));
            }
            groundRuleMap = getGroundRules(rules, substring, ConstantFactory.getConstMap().keySet());

            for (Map.Entry<Rule, List<List<Literal>>> ent : groundRuleMap.entrySet()) {
                for (List<Literal> clause : ent.getValue()) {
                    Literal head = null;
                    List<Literal> body = new LinkedList<>();
                    for (Literal lit : clause) {
                        if (lit.isNegated()) {
                            body.add(lit.negation());
                        } else {
                            head = lit;
                        }
                    }
                    Map<Rule, List<List<Literal>>> rule2groundings = head2Tails.get(head);
                    if (rule2groundings == null) {  //there is no such literal as a key
                        Map<Rule, List<List<Literal>>> ruleWithGroundings = new HashMap<>();
                        List<List<Literal>> bodies = new ArrayList<>();
                        bodies.add(body);
                        ruleWithGroundings.put(ent.getKey(), bodies);
                        head2Tails.put(head, ruleWithGroundings);
                    } else if (rule2groundings.get(ent.getKey()) == null) { //the literal has no such Rule
                        List<List<Literal>> bodies = new ArrayList<>();
                        bodies.add(body);
                        rule2groundings.put(ent.getKey(), bodies);
                    } else {    // just add the new ground rule to the list of ground rules for the respective Rule of the respective literal
                        rule2groundings.get(ent.getKey()).add(body);
                    }
                }
            }
        }

        Literal start = null;
        for (Literal head : head2Tails.keySet()) {
            if (head.toString().replaceAll(" ", "").equals(query)) {
                start = head;
            }
        }
        if (start == null) {
            Glogger.err("Warning, there are no entailing rules found for this query literal! - returning just the literal itself");
            String[] parseLiteral = Parser.parseLiteral(query);
            KL kl = TemplateFactory.predicatesByName.get(parseLiteral[0]);
            List<Variable> terms = new ArrayList<>();
            for (int i = 1; i < parseLiteral.length; i++) {
                Variable var = ConstantFactory.construct(parseLiteral[i]);
                terms.add(var);
            }
            GroundKL gkl = null;
            if (kl instanceof Kappa) {
                gkl = new GroundKappa((Kappa) kl, terms);
            } else {
                gkl = new GroundLambda((Lambda) kl, terms);
            }
            gkl.setValue(1.0);
            gkl.setValueAvg(1.0);
            return gkl;
        }
        GroundKL output = createGroundLRNN(start);
        Glogger.process("ground LRNN created");
        return output;
    }

    private GroundKL createGroundLRNN(Literal top) {
        int storedRecursiveLoopCount = recursiveLoopCount;

        KL kl = TemplateFactory.predicatesByName.get(top.predicate() + "/" + top.arity());

        if (openAtomSet.contains(top)) {
            Integer recCount = recursiveLoops.get(top);
            if (recCount == null) {
                recursiveLoops.put(top, 1);
            } else {
                recursiveLoops.put(top, recCount + 1);
            }
            recursiveLoopCount++;
            return null;
        } else {
            openAtomSet.add(top);
        }

        List<Variable> terms = new ArrayList<>();
        for (Term term : top.arguments()) {
            Variable var = ConstantFactory.construct(term.name());
            terms.add(var);
        }

        Map<Rule, List<List<Literal>>> rule2Bodies = head2Tails.get(top);

        GroundKL gkl = null;
        if (kl instanceof Kappa) {
            if (rule2Bodies == null) {
                SubK subK = new SubK((Kappa) kl, true);
                subK.setTerms(terms);
                gkl = facts.getFact(subK);
                if (gkl == null) {
                    gkl = new GroundKappa((Kappa) kl, terms);
                    gkl.setValueAvg(1.0);
                }
                openAtomSet.remove(top);
                Integer removed = recursiveLoops.remove(top);
                if (removed != null) {
                    recursiveLoopCount -= removed;
                }
                herbrandModel.put(top, gkl);
                return gkl;
            }
            gkl = new GroundKappa((Kappa) kl, terms);
            List<Tuple<HashSet<GroundLambda>, KappaRule>> allDisjuncts = new ArrayList<>();
            for (Map.Entry<Rule, List<List<Literal>>> ent : rule2Bodies.entrySet()) {
                KappaRule kr = (KappaRule) ent.getKey();
                HashSet<GroundLambda> grbodi = new HashSet<>();
                for (List<Literal> grbody : ent.getValue()) {
                    for (Literal literal : grbody) {    //KappaRule is flat - just one literal in the body
                        GroundKL saved = cache.get(literal);
                        if (saved != null) {
                            grbodi.add((GroundLambda) saved);
                            continue;
                        }
                        GroundLambda solved = (GroundLambda) createGroundLRNN(literal);
                        if (solved != null) {
                            grbodi.add(solved);
                        } else if (caching && (storedRecursiveLoopCount == recursiveLoopCount && solved == null)) {
                            cache.put(literal, solved);
                        }
                    }
                }
                if (!grbodi.isEmpty()) {
                    allDisjuncts.add(new Tuple(grbodi, kr));
                }
            }
            if (!allDisjuncts.isEmpty()) {
                ((GroundKappa) gkl).setDisjunctsAvg(allDisjuncts);
            } else {
                openAtomSet.remove(top);
                Integer removed = recursiveLoops.remove(top);
                if (removed != null) {
                    recursiveLoopCount -= removed;
                }
                return null;
            }
        } else {
            if (rule2Bodies == null) {
                SubL subL = new SubL((Lambda) kl, true);
                subL.setTerms(terms);
                gkl = facts.getFact(subL);
                if (gkl == null) {
                    gkl = new GroundLambda((Lambda) kl, terms);
                    gkl.setValueAvg(1.0);
                }
                openAtomSet.remove(top);
                Integer removed = recursiveLoops.remove(top);
                if (removed != null) {
                    recursiveLoopCount -= removed;
                }
                herbrandModel.put(top, gkl);
                return gkl;
            }
            gkl = new GroundLambda((Lambda) kl, terms);
            HashMap<GroundKappa, Integer> allConjuncts = new HashMap<>();
            int count = 0;
            for (Map.Entry<Rule, List<List<Literal>>> ent : rule2Bodies.entrySet()) {   //GroundLambda has just one LambdaRule
                for (List<Literal> grbody : ent.getValue()) {   //all the ground bodies become flattened in the compressed representation
                    count++;
                    List<GroundKappa> body = new ArrayList<>(grbody.size());
                    for (Literal literal : grbody) {
                        GroundKL saved = cache.get(literal);
                        if (saved != null) {
                            body.add((GroundKappa) saved);
                            continue;
                        }
                        GroundKappa solved = (GroundKappa) createGroundLRNN(literal);
                        if (solved != null) {
                            body.add(solved);
                        } else {
                            if (caching && (storedRecursiveLoopCount == recursiveLoopCount && solved == null)) {
                                cache.put(literal, solved);
                            }
                            body = null;
                            break;
                        }
                    }
                    if (body != null) {
                        for (GroundKappa gk : body) {
                            Integer get = allConjuncts.get(gk);
                            if (get != null) {
                                allConjuncts.put(gk, get + 1);
                            } else {
                                allConjuncts.put(gk, 1);
                            }
                        }
                    }
                }
            }
            if (!allConjuncts.isEmpty()) {
                ((GroundLambda) gkl).setConjunctsAvg(allConjuncts);
                ((GroundLambda) gkl).setConjunctsCountForAvg(count);
            } else {
                openAtomSet.remove(top);
                Integer removed = recursiveLoops.remove(top);
                if (removed != null) {
                    recursiveLoopCount -= removed;
                }
                return null;
            }
        }
        openAtomSet.remove(top);
        Integer removed = recursiveLoops.remove(top);
        if (removed != null) {
            recursiveLoopCount -= removed;
        }
        herbrandModel.put(top, gkl);
        return gkl;
    }

    /**
     * requires ConstantFactory to be load up and filled with all constant names
     * as well as TemplateFactory.predicatesByName
     *
     * @param rules
     * @param facts
     * @return
     */
    public Set<SubKL> getLRNNcache(List<Rule> rules, String facts) {
        Set<SubKL> cache = new HashSet<>();
        if (herbrandModel == null) {
            herbrandModel = getHerbrandModel(rules, facts, ConstantFactory.getConstMap().keySet());
        }
        for (Literal literal : herbrandModel.keySet()) {
            if (literal.predicate().equals("exists")) {
                continue;
            }
            KL kl = TemplateFactory.predicatesByName.get(literal.predicate() + "/" + literal.arity());
            SubKL subkl;
            if (kl instanceof Kappa) {
                subkl = new SubK((Kappa) kl, true);
            } else {
                subkl = new SubL((Lambda) kl, true);
            }
            for (Term term : literal.arguments()) {
                Variable var = ConstantFactory.construct(term.name());
                subkl.addVariable(var);
            }
            cache.add(subkl);
        }
        Glogger.process("lrnn cache created");
        return cache;
    }

    public Map<Literal, GroundKL> getHerbrandModel(List<Rule> rules, String facts, Set<String> allConstants) {
        Pair<List<Clause>, Clause> clauseRepresentation = getClauseRepresentationFromLRNNStrings(rules, facts, allConstants);

        return getHerbrandModel(clauseRepresentation.r, clauseRepresentation.s);
    }

    public Map<Rule, List<List<Literal>>> getGroundRules(List<Rule> rules, String facts, Set<String> allConstants) {
        Pair<List<Clause>, Clause> clauseRepresentation = getClauseRepresentationFromLRNNStrings(rules, facts, allConstants);
        if (herbrandModel == null) {
            herbrandModel = getHerbrandModel(clauseRepresentation.r, clauseRepresentation.s);
        }
        return getGroundRules(herbrandModel, rules, clauseRepresentation.r);
    }

    public Map<Rule, List<List<Literal>>> getGroundRules(Map<Literal, GroundKL> herbrand, List<Rule> rules, List<Clause> clauses) {
        Clause herbrandBase = new Clause(herbrand.keySet());
        Map<Rule, List<List<Literal>>> groundRules = new LinkedHashMap<>();
        Matching m = new Matching();
        if (rules.size() != clauses.size()) {
            Glogger.err("warning - mismatch in size matching of rule and clause lists: " + rules.size() + " vs " + clauses.size());
        }
        for (int i = 0; i < rules.size(); i++) {
            List<List<Literal>> grRules = new ArrayList<>();
            groundRules.put(rules.get(i), grRules);
            Pair<Term[], List<Term[]>> pair = m.allSubstitutions(removeNegationsFromClause(clauses.get(i)), herbrandBase);
            for (Term[] substitution : pair.s) {

                //Clause grRule = LogicUtils.substitute(clauses.get(i), pair.r, substitution);
                List<Literal> lits = getMySubstitutions(clauses.get(i), pair.r, substitution);

                grRules.add(lits);
            }
        }
        Glogger.process("ground rules created");
        return groundRules;
    }

    public Map<Literal, GroundKL> getHerbrandModel(List<Clause> irules, Clause groundFacts) {
        List<Clause> rules = new ArrayList<>(irules);
        for (Literal l : groundFacts.literals()) {
            rules.add(new Clause(l));
        }

        Map<Literal, GroundKL> herbrand = new HashMap<>();
        BottomUpGrounder bug = new BottomUpGrounder();
        Set<Literal> allLiterals = bug.herbrandModel(rules);
        for (Literal literal : allLiterals) {
            herbrand.put(literal, null);
        }

        Glogger.process("Herbrand model created");

        return herbrand;

    }

    private Clause removeNegationsFromClause(Clause rule) {
        LinkedHashSet<Literal> literals = new LinkedHashSet<>();
        for (Literal lit : rule.literals()) {
            if (lit.isNegated()) {
                lit = lit.negation();
            }
            literals.add(lit);
        }
        return new Clause(literals);
    }

    public Clause getClauseFromRuleString(String line) {
        int weightLen = Parser.getWeightLen(line);
        Pair<List<Literal>, List<Literal>> rule = PrologParser.parseLine(line.substring(weightLen));
        List<Literal> literals = new ArrayList<>();
        literals.addAll(rule.r);
        if (rule.s != null) {
            for (Literal literal : rule.s) {
                literals.add(literal.negation());
            }
        }
        return new Clause(literals);
    }

    private Pair<List<Clause>, Clause> getClauseRepresentationFromLRNNStrings(List<Rule> rules, String facts, Set<String> allConstants) {
        List<Clause> clauses = new ArrayList<>();
        for (Rule rule : rules) {
            Clause clause = getClauseFromRuleString(rule.toString());
            clauses.add(clause);
        }

        for (String extra : extraRules) {
            Glogger.process("adding extra rule: " + extra);
            Clause clause = getClauseFromRuleString(extra);
            clauses.add(clause);
        }

        StringBuilder sb = new StringBuilder();
        for (String constant : allConstants) {
            sb.append("exists(").append(constant).append("),");
        }
        if (facts != null) {
            sb.append(facts);
        }

        Clause ground = Clause.parse(sb.toString());
        Glogger.process("clause representation created");
        return new Pair<>(clauses, ground);
    }

    private int containsLiteralCount(String rule, String literalName) {
        Pattern p = Pattern.compile(literalName + "\\(");
        Matcher m = p.matcher(rule);
        int count = 0;
        while (m.find()) {
            count += 1;
        }
        return count;
    }

    private List<Literal> getMySubstitutions(Clause c, Term[] a, Term[] b) {
        Map<Term, Term> substitution = new HashMap<>();
        for (int i = 0; i < a.length; i++) {
            substitution.put(a[i], b[i]);
        }

        List<Literal> literals = new ArrayList<Literal>();
        for (Literal l : c.literals()) {
            Literal cl = l.copy();
            for (int j = 0; j < l.arity(); j++) {
                if (substitution.containsKey(l.get(j))) {
                    cl.set(substitution.get(l.get(j)), j);
                }
            }
            literals.add(cl);
        }
        return literals;
    }

    public Collection<GroundKL> getBtmUpCache() {
        return herbrandModel.values();
    }

    public void precalculateSimilars() {
        Map<String, double[]> embeddings = ConstantFactory.getEmbeddings();
        List<SubKL> literals = new ArrayList<>();
        double[] values = new double[embeddings.size() * embeddings.size()];
        int i = 0;
        for (Map.Entry<String, double[]> ent1 : embeddings.entrySet()) {
            for (Map.Entry<String, double[]> ent2 : embeddings.entrySet()) {
                double value = SimilarityPredicate.getSimilarity(ent1.getKey(), ent2.getKey());

                KL kl = TemplateFactory.predicatesByName.get("similar/3");
                List<Variable> terms = new ArrayList<>();

                Variable var = ConstantFactory.construct(ent1.getKey());
                terms.add(var);
                var = ConstantFactory.construct(ent2.getKey());
                terms.add(var);
                
                var = ConstantFactory.construct(value + "");
                terms.add(var);

                GroundKL gkl = null;
                if (kl instanceof Kappa) {
                    gkl = new GroundKappa((Kappa) kl, terms);
                } else {
                    gkl = new GroundLambda((Lambda) kl, terms);
                }
                gkl.setValue(value);
                gkl.setValueAvg(value);
                values[i++] = value;
            }
        }
        facts.setWeightedFacts(values, literals);
    }
}
