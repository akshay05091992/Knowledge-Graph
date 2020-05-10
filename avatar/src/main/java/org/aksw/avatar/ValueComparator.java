package org.aksw.avatar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.graph.Node;

public class ValueComparator<T1,T2 extends Comparable<T2>> implements Comparator<T1> {
    Map<Node, Integer> base;
    public ValueComparator(HashMap<Node, Integer> counts) {
        this.base = counts;
    }

    @Override
    public int compare(T1 k1, T1 k2) {
        Integer val1 = base.get(k1);
        Integer val2 = base.get(k2);

        return val1.compareTo(val2);
    }
}
