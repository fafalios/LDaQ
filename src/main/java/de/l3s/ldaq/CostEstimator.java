/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.l3s.ldaq;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.mem.TupleSlot;
import org.apache.jena.sparql.syntax.Element;

/**
 *
 * @author Fafalios
 */
public class CostEstimator {

    private int cost = 0;
    private int prevCost = 1;

    private int AVG_NUM_OF_ENTITY_OUTGOINING_PROPERTIES = 10;
    private int AVG_NUM_OF_ENTITY_INCOMING_PROPERTIES = 5;
    private int AVG_NUM_OF_SUBJECT_BINDINGS_FOR_A_GIVEN_PRED_OBJECT = 20;
    private int AVG_NUM_OF_SUBJECT_BINDINGS_FOR_RDF_TYPE = 50;
    private int AVG_NUM_OF_OBJECT_BINDINGS_FOR_A_GIVEN_SUBJECT_PRED = 20;

    public HashSet<VarBindingData> boundVars = new HashSet<>();
    public HashSet<String> URIs = new HashSet<>();
    public HashSet<Node> resolvedVars = new HashSet<>();
    public ArrayList<TriplePath> pendingTriples = new ArrayList<>();

    public static void main(String[] args) throws FileNotFoundException, IOException {

        String queryStr = "SELECT ?player ?birthDate WHERE { "
                + " ?player a <http://dbpedia.org/ontology/BasketballPlayer> ; "
                + "         <http://dbpedia.org/ontology/birthPlace> ?birthPlace . "
                + " ?birthPlace <http://dbpedia.org/property/capital> ?capital } ";
        Query query = QueryFactory.create(queryStr);

        CostEstimator costEstimator = new CostEstimator();
        int cost = costEstimator.estimate(query.getQueryPattern());
        System.out.println("===================");
        System.out.println("# Cost = " + cost);
        System.out.println("===================");
    }

    private boolean isBoundVar(Node var) {
        for (VarBindingData bv : boundVars) {
            if (bv.getSubjectOrObject().equals(var)) {
                return true;
            }
        }
        return false;
    }

    private void addVarData(Node var, Node predicate, TupleSlot ts, Node otherNode) {
        for (VarBindingData bv : boundVars) {
            if (bv.getSubjectOrObject().equals(var)) {
                bv.addPredicate(predicate);
                bv.addPositionInTriple(ts);
                bv.addOtherNode(otherNode);
                return;
            }
        }
    }

    public int estimateNumOfBindings(Node var) {
        // estimate cost based on type of predicate, previous cost and other factors 

        int numOfBindings = 0;
        for (VarBindingData bv : boundVars) {
            if (bv.getSubjectOrObject().equals(var)) {
                System.out.println("\t# Estimating number of bindings of variable '" + var + "'...");
                //System.out.println("\t  # NOTE=>If multiple URIs were used to bind '" + var + ": consider as cost the MIN num of var bindings.");
                //System.out.println("\t  # NOTE=>If bindings of a variable were used to bind '" + var + "': check for STAR-JOINS of this variable; this would, for example, reduce the value of 'prevCost'.");
                System.out.println("\t# Bindings data: " + bv);

                if (bv.getPredicates().size() == 1) {
                    System.out.println("\t  ONE predicate was used to bind the variable! ");
                    Node predicate = bv.getPredicates().get(0);
                    TupleSlot positionInTriple = bv.getPositionsInTriple().get(0);
                    Node otherNode = bv.getOtherNode().get(0);

                    if (predicate.isURI()) { //the predicate is  a variable
                        System.out.println("\t  Predicate is a URI! ");
                        if (positionInTriple.equals(TupleSlot.SUBJECT)) {
                            System.out.println("\t  Variable was the SUBJECT in the triple! ");
                            if (predicate.getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {
                                numOfBindings = AVG_NUM_OF_SUBJECT_BINDINGS_FOR_RDF_TYPE;
                            } else {
                                numOfBindings = AVG_NUM_OF_SUBJECT_BINDINGS_FOR_A_GIVEN_PRED_OBJECT;
                            }
                        } else if (positionInTriple.equals(TupleSlot.OBJECT)) {
                            System.out.println("\t  Variable was the OBJECT in the triple! ");
                            numOfBindings = AVG_NUM_OF_OBJECT_BINDINGS_FOR_A_GIVEN_SUBJECT_PRED;
                        } else { // predicate
                            System.out.println("\t  Variable was the PREDICATE in the triple! ");
                            numOfBindings = AVG_NUM_OF_ENTITY_OUTGOINING_PROPERTIES;
                        }
                    } else { //the predicate is  a variable
                        System.out.println("\t  Predicate is a VARIABLE! ");
                        if (positionInTriple.equals(TupleSlot.SUBJECT)) {
                            System.out.println("\t  Variable was the SUBJECT in the triple! ");
                            numOfBindings = AVG_NUM_OF_SUBJECT_BINDINGS_FOR_A_GIVEN_PRED_OBJECT * AVG_NUM_OF_ENTITY_INCOMING_PROPERTIES;
                        } else if (positionInTriple.equals(TupleSlot.OBJECT)) {
                            System.out.println("\t  Variable was the OBJECT in the triple! ");
                            numOfBindings = AVG_NUM_OF_ENTITY_OUTGOINING_PROPERTIES * AVG_NUM_OF_OBJECT_BINDINGS_FOR_A_GIVEN_SUBJECT_PRED;
                        } else { // predicate
                            System.out.println("\t  Variable was the PREDICATE in the triple! ");
                            numOfBindings = AVG_NUM_OF_ENTITY_OUTGOINING_PROPERTIES;
                        }
                    }
                } else { //more complex case
                    System.out.println("\t  MULTIPLE predicates were used to bind the variable! ");
                    int pos = 0;
                    for (Node p : bv.getPredicates()) {
                        pos++;
                        // ... ... 
                    }
                }

            }
        }

        int tempCost = numOfBindings * prevCost;
        prevCost = tempCost;
        return tempCost;
    }

    public boolean checkTriple(TriplePath triple) {

        Node subject = triple.getSubject();
        Node predicate = triple.getPredicate();
        Node object = triple.getObject();

        if (subject.isURI() || object.isURI()) {
            System.out.println("\t# We need to resolve one of the URIs in the triple, thus we increase the cost if the URI is new.");
            String uri;
            if (subject.isURI()) {
                uri = subject.getURI().toLowerCase();
            } else {
                uri = object.getURI().toLowerCase();
            }
            if (!URIs.contains(uri)) {
                cost++;
                URIs.add(uri);
            }
            if (object.isVariable()) {
                System.out.println("\t# The variable '" + object + "' can get bound.");
                if (isBoundVar(object)) {
                    addVarData(object, predicate, TupleSlot.OBJECT, subject);
                } else {
                    VarBindingData vbd = new VarBindingData(object, predicate, TupleSlot.OBJECT, subject);
                    boundVars.add(vbd);
                }

                if (predicate.isVariable()) {
                    System.out.println("\t# The variable '" + predicate + "' can get bound.");
                    if (isBoundVar(predicate)) {
                        addVarData(predicate, predicate, TupleSlot.PREDICATE, null);
                    } else {
                        VarBindingData vbd = new VarBindingData(predicate, null, TupleSlot.PREDICATE, subject);
                        boundVars.add(vbd);
                    }
                }
            }

            if (subject.isVariable()) {
                System.out.println("\t# The variable '" + subject + "' can get bound.");
                if (isBoundVar(subject)) {
                    addVarData(subject, predicate, TupleSlot.SUBJECT, object);
                } else {
                    VarBindingData vbd = new VarBindingData(subject, predicate, TupleSlot.SUBJECT, object);
                    boundVars.add(vbd);
                }

                if (predicate.isVariable()) {
                    System.out.println("\t# The variable '" + predicate + "' can get bound.");
                    if (isBoundVar(predicate)) {
                        addVarData(predicate, predicate, TupleSlot.PREDICATE, null);
                    } else {
                        VarBindingData vbd = new VarBindingData(predicate, null, TupleSlot.PREDICATE, object);
                        boundVars.add(vbd);
                    }
                }
            }

            return true;
        } else if (subject.isVariable() && object.isVariable()) {
            if (isBoundVar(subject) && !isBoundVar(object)) {
                if (!resolvedVars.contains(subject)) {
                    System.out.println("\t# We need to resolve the URI bindings of the variable " + subject);
                    resolvedVars.add(subject);

                    cost += estimateNumOfBindings(subject);
                }
                System.out.println("\t# The variable '" + object + "' can get bound.");

                VarBindingData vbd = new VarBindingData(object, predicate, TupleSlot.OBJECT, subject);
                boundVars.add(vbd);

                if (predicate.isVariable()) {
                    System.out.println("\t# The variable '" + predicate + "' can get bound.");
                    if (isBoundVar(predicate)) {
                        addVarData(predicate, predicate, TupleSlot.PREDICATE, null);
                    } else {
                        VarBindingData vbdPred = new VarBindingData(predicate, null, TupleSlot.PREDICATE, subject);
                        boundVars.add(vbdPred);
                    }
                }
                // the subject variable was already bound, thus we have a join!
                return true;
            }

            if (isBoundVar(object) && !isBoundVar(subject)) {
                if (!resolvedVars.contains(object)) {
                    System.out.println("\t# We need to resolve the URI bindings of the variable " + object);
                    resolvedVars.add(object);

                    cost += estimateNumOfBindings(subject);
                }
                System.out.println("\t# The variable '" + subject + "' can get bound.");

                VarBindingData vbd = new VarBindingData(subject, predicate, TupleSlot.SUBJECT, object);
                boundVars.add(vbd);

                if (predicate.isVariable()) {
                    System.out.println("\t# The variable '" + predicate + "' can get bound.");
                    if (isBoundVar(predicate)) {
                        addVarData(predicate, predicate, TupleSlot.PREDICATE, null);
                    } else {
                        VarBindingData vbdPre = new VarBindingData(predicate, null, TupleSlot.PREDICATE, object);
                        boundVars.add(vbdPre);
                    }
                }
                return true;
            }

            if (!isBoundVar(subject) && !isBoundVar(object)) {
                System.out.println("\t# Adding the triple in the list of pending triples...");
                return false;
            }

            if (isBoundVar(subject) && isBoundVar(object)) {
                System.out.println("\t# No action is needed.");
                // nothing 
                return true;
            }

        } else { // subject is a VARIABLE and object is a LITERAL 
            if (isBoundVar(subject)) {
                if (!resolvedVars.contains(subject)) {
                    System.out.println("\t# We need to resolve the URI bindings of the variable " + subject);
                    resolvedVars.add(subject);

                    cost += estimateNumOfBindings(subject);
                }
                System.out.println("\t# Adding additional triple information for the variable '" + subject + "'.");
                addVarData(subject, predicate, TupleSlot.SUBJECT, object);
                if (predicate.isVariable()) {
                    System.out.println("\t# The variable '" + predicate + "' can get bound.");
                    if (isBoundVar(predicate)) {
                        addVarData(predicate, predicate, TupleSlot.PREDICATE, null);
                    } else {
                        VarBindingData vbd = new VarBindingData(predicate, null, TupleSlot.PREDICATE, subject);
                        boundVars.add(vbd);
                    }
                }
                return true;
            } else {
                System.out.println("\t# Adding the triple in the list of pending triples...");
                return false;
            }
        }
        return true;
    }

    public int estimate(Element graphPattern) {

        ArrayList<TriplePath> triples = CheckAnswerability.getTriples(graphPattern);
        for (TriplePath triple : triples) {
            System.out.println("-------------------------------");
            System.out.println("# Triple: " + triple);
            System.out.println("# Status:");
            System.out.println("  - Bound vars: " + boundVars);
            System.out.println("  - Resolved vars: " + resolvedVars);
            System.out.println("  - Pending triples: " + pendingTriples);

            boolean okay = checkTriple(triple);
            if (!okay) {
                pendingTriples.add(triple);
            }
        }

        if (!pendingTriples.isEmpty()) {
            System.out.println("\n-------------------------------");
            System.out.println("# Start Checking pending triples...");
        }
        while (!pendingTriples.isEmpty()) {

            ArrayList<TriplePath> toRemove = new ArrayList<>();
            for (TriplePath triple : pendingTriples) {
                System.out.println("-------------------------------");
                System.out.println("# Triple: " + triple);
                System.out.println("# Status:");
                System.out.println("  - Bound vars: " + boundVars);
                System.out.println("  - Resolved vars: " + resolvedVars);
                System.out.println("  - Pending triples: " + pendingTriples);

                boolean okay = checkTriple(triple);
                if (okay) {
                    toRemove.add(triple);
                }
            }
            pendingTriples.removeAll(toRemove);
        }

        return cost;
    }

}
