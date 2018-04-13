package ru.avicomp.map.spin;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.util.graph.GraphListenerBase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * To produce good looking model.
 * TODO: currently it is just for fun.
 * It is not thread-safe.
 * <p>
 * Created by @szuev on 12.04.2018.
 */
public class PrefixedGraphListener extends GraphListenerBase {
    private final PrefixMapping prefixes;
    private final NodePrefixMapper extractor;

    private Map<String, AtomicLong> map = new HashMap<>();

    public PrefixedGraphListener(PrefixMapping prefixes, NodePrefixMapper extractor) {
        this.prefixes = prefixes;
        this.extractor = extractor;
    }

    @Override
    protected void addEvent(Triple t) {
        asIterable(t).forEach(n -> {
            String pref = extractor.extract(n);
            if (pref == null) return;
            if (map.computeIfAbsent(pref, i -> new AtomicLong()).incrementAndGet() != 1)
                return;
            prefixes.setNsPrefix(pref, extractor.getNameSpace(n));
        });
    }

    @Override
    protected void deleteEvent(Triple t) {
        asIterable(t).forEach(n -> {
            String pref = extractor.extract(n);
            if (pref == null) return;
            if (map.computeIfAbsent(pref, i -> new AtomicLong()).decrementAndGet() > 0)
                return;
            map.remove(pref);
            prefixes.removeNsPrefix(pref);
        });
    }

    public static Iterable<Node> asIterable(Triple t) {
        Set<Node> res = new HashSet<>(3);
        res.add(t.getSubject());
        res.add(t.getPredicate());
        res.add(t.getObject());
        return res;
    }
}
