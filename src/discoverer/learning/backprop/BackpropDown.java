/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package discoverer.learning.backprop;

import discoverer.grounding.evaluation.GroundedTemplate;
import discoverer.construction.example.Example;
import discoverer.global.Global;
import discoverer.grounding.network.GroundKL;
import discoverer.grounding.network.GroundKappa;
import discoverer.grounding.network.GroundLambda;
import discoverer.construction.template.rules.KappaRule;
import discoverer.global.Glogger;
import discoverer.global.Settings;
import discoverer.learning.functions.Activations;
import discoverer.global.Tuple;
import discoverer.learning.Weights;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Gusta
 */
public class BackpropDown {

    private static Weights weights = new Weights(); //storing intermediate weight updates(Kappa + Double tuple updates)
    static double learnRate;

    public static Weights getNewWeights(GroundedTemplate b, double targetVal) {
        learnRate = Settings.learnRate;
        weights.clear();
        GroundKL o = b.getLast(); //final Kappa node(assuming Kappa output only anyway)
        if (o == null) {
            return weights;
        }

        double baseDerivative = /*-1**/ (targetVal - b.valMax);  //output error-level derivative

        if (o instanceof GroundKappa) {
            derive((GroundKappa) o, Settings.learnRate * baseDerivative);
        } else {
            derive((GroundLambda) o, Settings.learnRate * baseDerivative);
        }

        return weights;
    }

    private static void derive(GroundKappa gk, double derivative) {
        if (gk.dropMe) {
            Glogger.debug("dropping " + gk);
            return;
        }

        if (Global.isDebugEnabled()) {
            System.out.println("deriving: " + gk);
        }

        if (!Global.weightedFacts && gk.isElement()) {
            return; //we do not update the weights for example atoms (but it would be possible and might be interesting)
        }

        gk.addGroundParentDerivative(derivative);   //aggregating(summing) the derivative from ground parent nodes
        gk.incrGroundParentsChecked();

        if (gk.getGroundParentsChecked() >= gk.getGroundParents()) { //all parents checked
            double firstDerivative = kappaActivationDerivative(gk);
            double myDerivative = gk.getGroundParentDerivative() * firstDerivative;
            
            if (gk.isElement()){
                weights.addW(gk, myDerivative);
                return;
            }
            
            weights.addW(gk.getGeneral(), myDerivative);   //updating offset weight (it's inner derivative is just 1, so no more computations needed)

            for (Tuple<GroundLambda, KappaRule> tup : gk.getDisjuncts()) {
                weights.addW(tup.y, myDerivative * tup.x.getValue());    //updating Kappa-rule's weight (it's inner derivative is just the value of corresponding GroundLambda)
                derive(tup.x, myDerivative * tup.y.getWeight());    //dive into solving the corresponding GroundLambda
            }
        }
    }

    private static void derive(GroundLambda gl, double derivative) {
        if (gl.dropMe) {
            Glogger.debug("dropping " + gl);
            return;
        }
        
        if (!Global.weightedFacts && gl.isElement()) {
            return; //we do not update the weights for example atoms (but it would be possible and might be interesting)
        }

        if (Global.isDebugEnabled()) {
            System.out.println("deriving: " + gl);
        }
        gl.addGroundParentDerivative(derivative);
        gl.incrGroundParentsChecked();

        if (gl.getGroundParentsChecked() == gl.getGroundParents()) {    //all parent's derivatives evaluated
            double firstDerivative = lambdaActivationDerivative(gl);
            
            if (gl.isElement()){
                weights.addW(gl, gl.getGroundParentDerivative() * firstDerivative);
                return;
            }
            
            for (GroundKappa gk : gl.getConjuncts()) {
                derive(gk, gl.getGroundParentDerivative() * firstDerivative);
            }
        }

    }

//-----------------------the actual-level derivative(no recursion) based on GroundKappa/Lambda's output value(within a derived activation function)
//---- the input value calculation could be skipped for the identity activation function derivative x -> 1, but generally is needed for x -> f'(x)
    private static double kappaActivationDerivative(GroundKappa gk) {
        //double result = gk.getGeneral().getOffset();
        List<Double> inputs = new ArrayList<>();
        for (Tuple<GroundLambda, KappaRule> t : gk.getDisjuncts()) {
            inputs.add(t.x.getValue() * t.y.getWeight());
            //result += t.x.getValue() * t.y.getWeight();     //we need to sum it up again because the value we have is after activaiton function
        }
        double result = Activations.kappaActivationDerived(inputs, gk.getGeneral().getOffset());    //and we need to feed it through a DERIVED activaiton function
        return result;
    }

    private static double lambdaActivationDerivative(GroundLambda gl) {
        //double result = gl.getGeneral().getOffset();
        List<Double> inputs = new ArrayList<>();
        for (GroundKappa gk : gl.getConjuncts()) {
            inputs.add(gk.getValue());
            //result += gk.getValue();
        }
        double result = Activations.lambdaActivationDerived(inputs, gl.getGeneral().getOffset());
        return result;
    }
}
