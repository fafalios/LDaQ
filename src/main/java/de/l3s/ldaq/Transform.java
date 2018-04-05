/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.l3s.ldaq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.sparql.syntax.ElementService;
import org.apache.jena.sparql.syntax.ElementUnion;
import org.apache.jena.sparql.syntax.ElementVisitorBase;
import org.apache.jena.sparql.syntax.ElementWalker;

/**
 * Transforms a LDaQ to a SPARQL-LD query
 *
 * @author Pavlos Fafalios
 */
public class Transform {

    /**
     * @param args the command line arguments
     *
     */
    public static void main(String[] args) {

        String queryStr = "SELECT ?player ?birthDate WHERE { ?player a <http://dbpedia.org/ontology/BasketballPlayer> ; <http://dbpedia.org/ontology/birthDate> ?birthDate }";
        Query query = QueryFactory.create(queryStr);

        Query sparqlldQuery = transformQuery(query);
        System.out.println("SPARQL-LD: "+ sparqlldQuery.toString());

    }

    public static Query transformQuery(Query query) {

        Element newpattern = transformQueryPattern(query.getQueryPattern());
        Query newQuery = new Query(query.getPrologue());
        newQuery.setQueryPattern(newpattern);
        return newQuery;
    }

    public static ElementGroup transformQueryPattern(Element graphPattern) {

        HashMap<String, ElementFilter> filters = getFilters(graphPattern);
        ArrayList<Element> pending = new ArrayList<>();
        HashSet<Node> B = new HashSet<>();
        LinkedHashMap<String, Element> services = new LinkedHashMap<>();

        ArrayList<Element> allElements = CheckAnswerability.getTripleAndUnionElements(graphPattern);
        for (Element el : allElements) {
            if (el.getClass() == ElementPathBlock.class) {
                boolean is = CheckAnswerability.isLinkedDataAnswerableBGP(el, false, B);
                if (is) {
                    include(services, el, B, filters);
                } else {
                    pending.add(el);
                }

            } else { // UNION
                List<Element> unionPatterns = ((ElementUnion) el).getElements();
                boolean allOk = true;
                HashSet<Node> unionVars = new HashSet<>();
                for (Element unionEl : unionPatterns) {
                    if (!CheckAnswerability.isLinkedDataAnswerableBGP(unionEl, true, B)) {
                        allOk = false;
                        break;
                    }
                    unionVars.addAll(CheckAnswerability.getAllVariables(unionEl));
                }
                if (allOk) {
                    B.addAll(unionVars);
                    include(services, el, B, filters);
                } else {
                    pending.add(el);
                }
            }
        }

        while (!pending.isEmpty()) {
            ArrayList<Element> toremove = new ArrayList<>();
            for (Element pendingEl : pending) {
                if (pendingEl.getClass() == ElementPathBlock.class) {
                    boolean is = CheckAnswerability.isLinkedDataAnswerableBGP(pendingEl, false, B);
                    if (is) {
                        include(services, pendingEl, B, filters);
                        toremove.add(pendingEl);
                    }
                } else { // union
                    List<Element> unionPatterns = ((ElementUnion) pendingEl).getElements();
                    boolean allOk = true;
                    HashSet<Node> unionVars = new HashSet<>();
                    for (Element unionEl : unionPatterns) {
                        if (!CheckAnswerability.isLinkedDataAnswerableBGP(unionEl, true, B)) {
                            allOk = false;
                            break;
                        }
                        unionVars.addAll(CheckAnswerability.getAllVariables(unionEl));
                    }
                    if (allOk) {
                        B.addAll(unionVars);
                        include(services, pendingEl, B, filters);
                        toremove.add(pendingEl);
                    }
                }
            }
            pending.removeAll(toremove);
        }

        ElementGroup newQueryPattern = new ElementGroup();
        services.keySet().stream().map((varOrUri) -> services.get(varOrUri)).forEach((serv) -> {
            newQueryPattern.addElement(serv);
        });
        return newQueryPattern;
    }

    public static void include(LinkedHashMap<String, Element> services, Element el, HashSet<Node> B, HashMap<String, ElementFilter> filters) {

        if (el.getClass() == ElementPathBlock.class) { // TRIPLE
            TriplePath triple = ((ElementPathBlock) el).getPattern().getList().get(0);
            Node subject = triple.getSubject();
            Node object = triple.getObject();

            if (subject.isURI() || object.isURI()) {
                String uri = subject.isURI() ? subject.getURI() : object.getURI();
                if (services.containsKey(uri)) {
                    ((ElementGroup) ((ElementService) services.get(uri)).getElement()).addTriplePattern(triple.asTriple());
                    checkAndAddFilter(subject, object, (ElementService) services.get(uri), filters);
                } else {
                    ElementGroup group = new ElementGroup();
                    group.addTriplePattern(triple.asTriple());
                    ElementService service = new ElementService(uri, group);
                    checkAndAddFilter(subject, object, service, filters);
                    services.put(uri, service);
                }
            } else if (B.contains(subject) || B.contains(object)) {
                Node var = B.contains(subject) ? subject : object;
                if (services.containsKey(var.getName())) {
                    ((ElementGroup) ((ElementService) services.get(var.getName())).getElement()).addTriplePattern(triple.asTriple());
                    checkAndAddFilter(subject, object, (ElementService) services.get(var.getName()), filters);
                } else {
                    ElementGroup group = new ElementGroup();
                    group.addTriplePattern(triple.asTriple());
                    ElementService service = new ElementService(var, group, false);
                    checkAndAddFilter(subject, object, service, filters);
                    services.put(var.getName(), service);
                }
            }
        } else { // UNION group

            Node var = getCommonBoundVariable(el, B, services);
            if (var == null) { // CASE OF URI
                List<Element> unionPatterns = ((ElementUnion) el).getElements();
                ElementUnion newElemUn = new ElementUnion();
                for (Element unionEl : unionPatterns) {
                    Element serviceElem = transformBGP(unionEl);
                    newElemUn.addElement(serviceElem);
                }
                services.put("UNION", newElemUn);

            } else if (services.containsKey(var.getName())) {
                ((ElementGroup) ((ElementService) services.get(var.getName())).getElement()).addElement(el);
                //checkAndAddFilter(subject, object, (ElementService) services.get(var.getName()), filters);
            } else {
                ElementGroup group = new ElementGroup();
                group.addElement(el);
                ElementService service = new ElementService(var, group, false);
                //checkAndAddFilter(subject, object, service, filters);
                services.put(var.getName(), service);
            }
        }
    }

    public static Node getCommonBoundVariable(Element el, HashSet<Node> B, LinkedHashMap<String, Element> services) {

        List<Element> unionPatterns = ((ElementUnion) el).getElements();
        HashSet<Node> commonVars = new HashSet<>();
        for (Element unionEl : unionPatterns) {
            HashSet<Node> vars = CheckAnswerability.getAllVariables(unionEl);
            if (commonVars.isEmpty()) {
                commonVars.addAll(vars);
            } else {
                commonVars.retainAll(vars);
            }
        }
        Node var = null;
        for (Node v : commonVars) {
            if (B.contains(v)) {
                for (Element s : services.values()) {
                    if (s.toString().contains(v.getName())) {
                        var = v;
                        break;
                    }
                }
            }
        }
        return var;
    }

    public static ElementGroup transformBGP(Element graphPattern) {

        ArrayList<TriplePath> triples = getTriples(graphPattern);
        HashMap<String, ElementFilter> filters = getFilters(graphPattern);

        HashSet<Node> B = new HashSet<>();
        ArrayList<TriplePath> pending = new ArrayList<>();
        LinkedHashMap<String, ElementService> services = new LinkedHashMap<>();

        for (TriplePath triple : triples) {
            Node subject = triple.getSubject();
            Node predicate = triple.getPredicate();
            Node object = triple.getObject();
            if (subject.isURI() || object.isURI()) {
                String uri = subject.isURI() ? subject.getURI() : object.getURI();
                if (services.containsKey(uri)) {
                    ((ElementGroup) services.get(uri).getElement()).addTriplePattern(triple.asTriple());
                    checkAndAddFilter(subject, object, services.get(uri), filters);
                } else {
                    ElementGroup group = new ElementGroup();
                    group.addTriplePattern(triple.asTriple());
                    ElementService service = new ElementService(uri, group);
                    checkAndAddFilter(subject, object, service, filters);
                    services.put(uri, service);
                }

                if (subject.isURI()) {
                    if (object.isVariable()) {
                        B.add(object);
                    }
                    if (predicate.isVariable()) {
                        B.add(predicate);
                    }
                } else {
                    if (subject.isVariable()) {
                        B.add(subject);
                    }
                    if (predicate.isVariable()) {
                        B.add(predicate);
                    }
                }
            } else if (B.contains(subject) || B.contains(object)) {
                Node var = B.contains(subject) ? subject : object;
                if (services.containsKey(var.getName())) {
                    ((ElementGroup) services.get(var.getName()).getElement()).addTriplePattern(triple.asTriple());
                    checkAndAddFilter(subject, object, services.get(var.getName()), filters);
                } else {
                    ElementGroup group = new ElementGroup();
                    group.addTriplePattern(triple.asTriple());
                    ElementService service = new ElementService(var, group, false);
                    checkAndAddFilter(subject, object, service, filters);
                    services.put(var.getName(), service);
                }
                if (B.contains(subject)) {
                    if (object.isVariable()) {
                        B.add(object);
                    }
                    if (predicate.isVariable()) {
                        B.add(predicate);
                    }
                } else {
                    if (subject.isVariable()) {
                        B.add(subject);
                    }
                    if (predicate.isVariable()) {
                        B.add(predicate);
                    }
                }

            } else {
                pending.add(triple);
            }
        }

        while (!pending.isEmpty()) {
            ArrayList<TriplePath> toRemove = new ArrayList<>();
            for (TriplePath pendingTriple : pending) {
                Node subject = pendingTriple.getSubject();
                Node predicate = pendingTriple.getPredicate();
                Node object = pendingTriple.getObject();

                if (B.contains(subject) || B.contains(object)) {
                    Node var = B.contains(subject) ? subject : object;
                    if (services.containsKey(var.getName())) {
                        ((ElementGroup) services.get(var.getName()).getElement()).addTriplePattern(pendingTriple.asTriple());
                        checkAndAddFilter(subject, object, services.get(var.getName()), filters);
                    } else {
                        ElementGroup group = new ElementGroup();
                        group.addTriplePattern(pendingTriple.asTriple());
                        ElementService service = new ElementService(var, group, false);
                        checkAndAddFilter(subject, object, service, filters);
                        services.put(var.getName(), service);
                    }

                    if (B.contains(subject)) {
                        if (object.isVariable()) {
                            B.add(object);
                        }
                        if (predicate.isVariable()) {
                            B.add(predicate);
                        }
                    } else {
                        if (subject.isVariable()) {
                            B.add(subject);
                        }
                        if (predicate.isVariable()) {
                            B.add(predicate);
                        }
                    }
                    toRemove.add(pendingTriple);
                }
            }
            pending.removeAll(toRemove);
        }

        ElementGroup newQueryPattern = new ElementGroup();
        services.keySet().stream().map((varOrUri) -> services.get(varOrUri)).forEach((serv) -> {
            newQueryPattern.addElement(serv);
        });

        return newQueryPattern;
    }

    public static HashMap<String, ElementFilter> getFilters(Element graphPattern) {
        HashMap<String, ElementFilter> filters = new HashMap<>();
        ElementWalker.walk(graphPattern, new ElementVisitorBase() {
            @Override
            public void visit(ElementFilter el) {
                Expr expr = el.getExpr();
                Set<Var> vars = expr.getVarsMentioned();
                vars.stream().forEach((v) -> {
                    filters.put(v.asNode().getName(), el);
                });
            }
        });

        return filters;
    }

    public static ArrayList<TriplePath> getTriples(Element graphPattern) {
        ArrayList<TriplePath> triples = new ArrayList<>();
        ElementWalker.walk(graphPattern, new ElementVisitorBase() {

            @Override
            public void visit(ElementPathBlock el) {
                ListIterator<TriplePath> triplesIt = el.getPattern().iterator();
                while (triplesIt.hasNext()) {
                    TriplePath triple = triplesIt.next();
                    triples.add(triple);

                }
            }
        });
        return triples;
    }

    public static void checkAndAddFilter(Node subject, Node object, ElementService service, HashMap<String, ElementFilter> filters) {
        if (subject.isVariable()) {
            if (filters.containsKey(subject.getName())) {
                ((ElementGroup) service.getElement()).addElement(filters.get(subject.getName()));
            }
        }
        if (object.isVariable()) {
            if (filters.containsKey(object.getName())) {
                ((ElementGroup) service.getElement()).addElement(filters.get(object.getName()));
            }
        }
    }

}
