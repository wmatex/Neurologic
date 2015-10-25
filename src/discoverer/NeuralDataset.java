/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package discoverer;

import discoverer.construction.network.Kappa;
import discoverer.construction.network.LiftedNetwork;
import discoverer.construction.network.WeightInitializator;
import discoverer.construction.network.rules.KappaRule;
import discoverer.construction.network.rules.Rule;
import discoverer.grounding.network.groundNetwork.GroundNetwork;
import discoverer.grounding.network.groundNetwork.GroundNeuron;
import discoverer.learning.Sample;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author Gusta
 * this object is the only thing necessary for learning phase - for future memory tuning
 */
public class NeuralDataset {

    public GroundNetwork[] groundNetworks;
    public long timeToBuild;
    public GroundNetwork tmpActiveNet; //auxiliary to get reference from neurons to their mother network (without storing pointer in them cause of serialization)
    
    public double[] sharedWeights; //the shared sharedWeights
    
    public HashMap<Object, Integer> weightMapping;  //Kappa offsets and KappaRule's weights to indicies in sharedWeights


    public NeuralDataset(List<Sample> samples, LiftedNetwork network){
        createSharedWeights(network);
        makeNeuralNetworks(samples);
    }
    
    
    final void createSharedWeights(LiftedNetwork network) {
        int weightCounter = 0;
        for (Rule rule : network.rules) {
            if (rule instanceof KappaRule) {
                weightMapping.put(rule, weightCounter++);
            }
        }
        for (Kappa kappa : network.getKappas()) {
            weightMapping.put(kappa, weightCounter++);
        }

        sharedWeights = new double[weightCounter];
    }
    
    final void makeNeuralNetworks(List<Sample> samples) {
        groundNetworks = new GroundNetwork[samples.size()];
        int i = 0;
        samples.stream().forEach((sample) -> {
            groundNetworks[i] = new GroundNetwork();
            groundNetworks[i].allNeurons = new GroundNeuron[sample.getBall().groundNeurons.size()];
            tmpActiveNet = groundNetworks[i];
            groundNetworks[i].createNetwork(sample);
        });
        tmpActiveNet = null;
    }

    /**
     * reinitialize all kappa offests and kapparule sharedWeights of the
     * template
     */
    public void invalidateWeights() {
        for (int i = 0; i < sharedWeights.length; i++) {
            sharedWeights[i] = WeightInitializator.getWeight();
        }
    }
}