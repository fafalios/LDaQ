/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.l3s.ldaq;

import java.util.ArrayList;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.mem.TupleSlot;

/**
 *
 * @author fafalios
 */
public class VarBindingData {

    private Node subjectOrObject;
    private ArrayList<Node> predicates;
    private ArrayList<TupleSlot> positionsInTriple;
    private ArrayList<Node> elementUsedToBindOrJoin;

    public VarBindingData(Node subjectOrObject, Node predicate, TupleSlot positionInTriple, Node otherNode) {
        this.subjectOrObject = subjectOrObject;
        this.predicates = new ArrayList<>();
        this.predicates.add(predicate);
        this.positionsInTriple = new ArrayList<>();
        this.positionsInTriple.add(positionInTriple);
        this.elementUsedToBindOrJoin = new ArrayList<>();
        this.elementUsedToBindOrJoin.add(otherNode);
    }

    public Node getSubjectOrObject() {
        return subjectOrObject;
    }

    public void setSubjectOrObject(Node subjectOrObject) {
        this.subjectOrObject = subjectOrObject;
    }

    public ArrayList<Node> getPredicates() {
        return predicates;
    }

    public void setPredicates(ArrayList<Node> predicates) {
        this.predicates = predicates;
    }

    public ArrayList<TupleSlot> getPositionsInTriple() {
        return positionsInTriple;
    }

    public void setPositionsInTriple(ArrayList<TupleSlot> positionsInTriple) {
        this.positionsInTriple = positionsInTriple;
    }

    public ArrayList<Node> getOtherNode() {
        return elementUsedToBindOrJoin;
    }

    public void setOtherNode(ArrayList<Node> otherNode) {
        this.elementUsedToBindOrJoin = otherNode;
    }

    public void addPredicate(Node predicate) {
        this.predicates.add(predicate);
    }

    public void addPositionInTriple(TupleSlot positionInTriple) {
        this.positionsInTriple.add(positionInTriple);
    }

    public void addOtherNode(Node otherNode) {
        this.elementUsedToBindOrJoin.add(otherNode);
    }

    @Override
    public String toString() {
        return "VarBindingData{" + "subjectOrObject=" + subjectOrObject + ", predicates=" + predicates + ", positionsInTriple=" + positionsInTriple + ", elementUsedToBindOrJoin=" + elementUsedToBindOrJoin + '}';
    }

}
