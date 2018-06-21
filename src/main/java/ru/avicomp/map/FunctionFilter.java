package ru.avicomp.map;

import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.MapFunctionImpl;
import ru.avicomp.map.spin.vocabulary.AVC;

import java.util.function.Predicate;

/**
 * A helper to conduct functions filtering by some attribute.
 * Created by @szuev on 21.06.2018.
 *
 * @see MapManager#functions()
 */
public enum FunctionFilter implements Predicate<MapFunction> {
    BOOLEAN {
        @Override
        public boolean test(MapFunction f) {
            return f.isBoolean();
        }
    },
    DATE {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.DateFunctions);
        }
    },
    MATH {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.MathematicalFunctions);
        }
    },
    MISC {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl &&
                    (((MapFunctionImpl) f).isInheritedOfClass(SPL.MiscFunctions) || !((MapFunctionImpl) f).asResource().hasProperty(RDFS.subClassOf));
        }
    },
    URI {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.URIFunctions);
        }
    },
    ONTOLOGY {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.OntologyFunctions);
        }
    },
    STRING {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(SPL.StringFunctions);
        }
    },
    AGGREGATE {
        @Override
        public boolean test(MapFunction f) {
            return f instanceof MapFunctionImpl && ((MapFunctionImpl) f).isInheritedOfClass(AVC.AggregateFunctions);
        }
    },
    VARARG {
        @Override
        public boolean test(MapFunction f) {
            return f.isVararg();
        }
    },
    TARGET {
        @Override
        public boolean test(MapFunction f) {
            return f.isTarget();
        }
    };

    @Override
    public abstract boolean test(MapFunction function);
}
