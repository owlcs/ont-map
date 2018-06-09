package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.Managers;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.spin.MapFunctionImpl;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 04.06.2018.
 */
@RunWith(Parameterized.class)
public class FunctionsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionsTest.class);
    private final FuncTypeFilter data;

    public FunctionsTest(FuncTypeFilter data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static FuncTypeFilter[] data() {
        return FuncTypeFilter.values();
    }

    @Test
    public void testList() {
        MapManager manager = Managers.getMapManager();
        List<MapFunction> functions = manager.functions().filter(data::test).collect(Collectors.toList());
        functions.forEach(f -> LOGGER.debug("{}", f));
        LOGGER.info("{} : {}", data.name(), functions.size());
    }

    /**
     * @see ru.avicomp.map.spin.MapFunctionImpl#isInheritedOfClass(Resource)
     */
    enum FuncTypeFilter {
        ALL {
            @Override
            public boolean test(MapFunction f) {
                return true;
            }
        },
        BOOLEAN_RETURN_TYPE {
            @Override
            public boolean test(MapFunction f) {
                return f.isBoolean();
            }
        },
        BOOLEAN_CLASS {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.BooleanFunctions);
            }
        },
        DATE {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.DateFunctions);
            }
        },
        MATH {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.MathematicalFunctions);
            }
        },
        MISC {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.MiscFunctions);
            }
        },
        URI {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.URIFunctions);
            }
        },
        ONTOLOGY {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.OntologyFunctions);
            }
        },
        STRING {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.StringFunctions);
            }
        },
        TARGET {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPINMAP.TargetFunctions);
            }
        };

        public abstract boolean test(MapFunction f);
    }
}

