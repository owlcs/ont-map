package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Resource;
import org.junit.Assert;
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
import ru.avicomp.map.spin.vocabulary.AVC;

import java.util.Comparator;
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
        List<MapFunction> functions = manager.functions()
                .sorted(Comparator.comparing(MapFunction::type).thenComparing(MapFunction::name))
                .filter(data::test).collect(Collectors.toList());
        functions.forEach(f -> LOGGER.debug("{}", f));
        LOGGER.info("{} : {}", data.name(), functions.size());
        Assert.assertEquals(data.count, functions.size());
    }

    /**
     * @see ru.avicomp.map.spin.MapFunctionImpl#isInheritedOfClass(Resource)
     */
    enum FuncTypeFilter {
        ALL(195) {
            @Override
            public boolean test(MapFunction f) {
                return true;
            }
        },
        BOOLEAN_RETURN_TYPE(42) {
            @Override
            public boolean test(MapFunction f) {
                return f.isBoolean();
            }
        },
        BOOLEAN_CLASS(28) {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.BooleanFunctions);
            }
        },
        DATE(13) {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.DateFunctions);
            }
        },
        MATH(35) {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.MathematicalFunctions);
            }
        },
        MISC(20) {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.MiscFunctions);
            }
        },
        URI(5) {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.URIFunctions);
            }
        },
        ONTOLOGY(16) {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.OntologyFunctions);
            }
        },
        STRING(59) {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPL.StringFunctions);
            }
        },
        AGGREGATE(1) {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(AVC.AggregateFunctions);
            }
        },
        TARGET(10) {
            @Override
            public boolean test(MapFunction f) {
                return ((MapFunctionImpl) f).isInheritedOfClass(SPINMAP.TargetFunctions);
            }
        };
        // TODO: this count number will change during the development process
        private final int count;

        FuncTypeFilter(int count) {
            this.count = count;
        }

        public abstract boolean test(MapFunction f);
    }
}

