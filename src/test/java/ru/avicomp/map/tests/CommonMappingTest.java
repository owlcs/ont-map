package ru.avicomp.map.tests;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntID;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @szuev on 05.06.2018.
 */
@RunWith(Parameterized.class)
public class CommonMappingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonMappingTest.class);

    private final Data data;

    public CommonMappingTest(Data data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<Data> getData() {
        return Arrays.asList(
                of(UUIDMapTest.class, 1, 0),
                of(BuildURIMapTest.class, 1, 2),
                of(NestedFuncMapTest.class, 1, 1),
                of(ConditionalMapTest.class, 1, 3),
                of(RelatedContextMapTest.class, 2, 1),
                of(FilterDefaultMapTest.class, 1, 1),
                of(FilterIndividualsMapTest.class, 1, 1),
                of(SplitMapTest.class, 2, 2),
                of(IntersectConcatMapTest.class, 2, 2),
                of(GroupConcatTest.class, 1, 1),
                of(MathOpsMapTest.class, 1, 2),
                of(MultiContextMapTest.class, 8, 4),
                of(PropertyChainMapTest.class, 1, 2),
                of(VarArgMapTest.class, 1, 2)
        );
    }

    private static Data of(Class<? extends AbstractMapTest> clazz, int classBridgeNumber, int propertyBridgeBumber) {
        AbstractMapTest m;
        try {
            m = clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
        return new Data(m, classBridgeNumber, propertyBridgeBumber);
    }

    @Test
    public void testListMapping() {
        MapModel m = data.map.assembleMapping();
        OntID id = m.getID();
        // list contexts
        LOGGER.debug("Model <{}> ::: {}", id, m.contexts().map(Object::toString).collect(Collectors.joining(", ", "[", "]")));
        // list property-bridges
        m.contexts().forEach(c -> LOGGER.debug("Context properties ({}) :: {}", c, c.properties().map(Object::toString).collect(Collectors.joining(", ", "[", "]"))));

        // list mapping functions:
        m.contexts().forEach(c -> LOGGER.debug("Mapping function ({}) :: {}", c, c.getMapping()));

        Assert.assertEquals(data.contexts, m.contexts().count());
        Assert.assertEquals(data.properties, m.contexts().flatMap(Context::properties).distinct().count());
        LOGGER.debug("============");
        // list resources
        m.contexts().flatMap(c -> Stream.concat(Stream.of(c.asResource()), c.properties().map(p -> p.asResource())))
                .forEach(r -> LOGGER.debug("{} :: {}", r, TestUtils.toString(m.asOntModel(), r.getRoot())));
    }

    private static class Data {
        private final AbstractMapTest map;
        private final int contexts, properties;

        private Data(AbstractMapTest map, int contexts, int properties) {
            this.map = map;
            this.contexts = contexts;
            this.properties = properties;
        }

        @Override
        public String toString() {
            return map.getClass().getSimpleName();
        }
    }
}
