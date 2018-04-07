package ru.avicomp.map.spin;

import org.apache.jena.enhanced.Personality;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.model.*;
import org.topbraid.spin.model.impl.*;
import org.topbraid.spin.model.update.impl.*;
import org.topbraid.spin.util.SimpleImplementation;
import org.topbraid.spin.util.SimpleImplementation2;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.spin.impl.TargetFunctionImpl;
import ru.avicomp.map.spin.model.TargetFunction;
import ru.avicomp.map.spin.vocabulary.SP;
import ru.avicomp.map.spin.vocabulary.SPINMAP;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;

/**
 * Settings and personalities for an {@link Model} which contain spin rules and other stuff.
 * Created by @szuev on 07.04.2018.
 *
 * @see OntModelConfig
 */
public class SpinModelConfig {
    public static Personality<RDFNode> SPIN_PERSONALITY = init(OntModelConfig.STANDARD_PERSONALITY.copy());

    public static Model createModel(Graph graph) {
        return new ModelCom(graph, SPIN_PERSONALITY);
    }

    /**
     * @param p {@link Personality} to modify
     * @return {@link Personality} the same instance
     * @see org.topbraid.spin.vocabulary.SP#init(Personality)
     */
    public static Personality<RDFNode> init(Personality<RDFNode> p) {
        p.add(org.topbraid.spin.model.Aggregation.class, new SimpleImplementation(SPL.Argument.asNode(), AggregationImpl.class));
        p.add(Argument.class, new SimpleImplementation(SPL.Argument.asNode(), ArgumentImpl.class));
        p.add(Attribute.class, new SimpleImplementation(SPL.Attribute.asNode(), AttributeImpl.class));
        p.add(org.topbraid.spin.model.Ask.class, new SimpleImplementation(SP.Ask.asNode(), AskImpl.class));
        p.add(org.topbraid.spin.model.Bind.class, new SimpleImplementation2(SP.Bind.asNode(), SP.Let.asNode(), BindImpl.class));
        p.add(org.topbraid.spin.model.update.Clear.class, new SimpleImplementation(SP.Clear.asNode(), ClearImpl.class));
        p.add(org.topbraid.spin.model.Construct.class, new SimpleImplementation(SP.Construct.asNode(), ConstructImpl.class));
        p.add(org.topbraid.spin.model.update.Create.class, new SimpleImplementation(SP.Create.asNode(), CreateImpl.class));
        p.add(org.topbraid.spin.model.update.Delete.class, new SimpleImplementation(SP.Delete.asNode(), DeleteImpl.class));
        p.add(org.topbraid.spin.model.update.DeleteData.class, new SimpleImplementation(SP.DeleteData.asNode(), DeleteDataImpl.class));
        p.add(org.topbraid.spin.model.update.DeleteWhere.class, new SimpleImplementation(SP.DeleteWhere.asNode(), DeleteWhereImpl.class));
        p.add(Describe.class, new SimpleImplementation(SP.Describe.asNode(), DescribeImpl.class));
        p.add(org.topbraid.spin.model.update.Drop.class, new SimpleImplementation(SP.Drop.asNode(), DropImpl.class));
        p.add(ElementList.class, new SimpleImplementation(RDF.List.asNode(), ElementListImpl.class));
        p.add(Exists.class, new SimpleImplementation(SP.Exists.asNode(), ExistsImpl.class));
        p.add(Function.class, new SimpleImplementation(SPIN.Function.asNode(), FunctionImpl.class));
        p.add(TargetFunction.class, new SimpleImplementation(SPINMAP.TargetFunction.asNode(), TargetFunctionImpl.class));
        p.add(FunctionCall.class, new SimpleImplementation(SPIN.Function.asNode(), FunctionCallImpl.class));
        p.add(Filter.class, new SimpleImplementation(SP.Filter.asNode(), FilterImpl.class));
        p.add(org.topbraid.spin.model.update.Insert.class, new SimpleImplementation(SP.Insert.asNode(), InsertImpl.class));
        p.add(org.topbraid.spin.model.update.InsertData.class, new SimpleImplementation(SP.InsertData.asNode(), InsertDataImpl.class));
        p.add(org.topbraid.spin.model.update.Load.class, new SimpleImplementation(SP.Load.asNode(), LoadImpl.class));
        p.add(Minus.class, new SimpleImplementation(SP.Minus.asNode(), MinusImpl.class));
        p.add(org.topbraid.spin.model.update.Modify.class, new SimpleImplementation(SP.Modify.asNode(), ModifyImpl.class));
        p.add(Module.class, new SimpleImplementation(SPIN.Module.asNode(), ModuleImpl.class));
        p.add(NamedGraph.class, new SimpleImplementation(SP.NamedGraph.asNode(), NamedGraphImpl.class));
        p.add(NotExists.class, new SimpleImplementation(SP.NotExists.asNode(), NotExistsImpl.class));
        p.add(Optional.class, new SimpleImplementation(SP.Optional.asNode(), OptionalImpl.class));
        p.add(Service.class, new SimpleImplementation(SP.Service.asNode(), ServiceImpl.class));
        p.add(Select.class, new SimpleImplementation(SP.Select.asNode(), SelectImpl.class));
        p.add(SubQuery.class, new SimpleImplementation(SP.SubQuery.asNode(), SubQueryImpl.class));
        p.add(SPINInstance.class, new SimpleImplementation(RDFS.Resource.asNode(), SPINInstanceImpl.class));
        p.add(Template.class, new SimpleImplementation(SPIN.Template.asNode(), TemplateImpl.class));
        p.add(TemplateCall.class, new SimpleImplementation(RDFS.Resource.asNode(), TemplateCallImpl.class));
        p.add(TriplePath.class, new SimpleImplementation(SP.TriplePath.asNode(), TriplePathImpl.class));
        p.add(TriplePattern.class, new SimpleImplementation(SP.TriplePattern.asNode(), TriplePatternImpl.class));
        p.add(TripleTemplate.class, new SimpleImplementation(SP.TripleTemplate.asNode(), TripleTemplateImpl.class));
        p.add(Union.class, new SimpleImplementation(SP.Union.asNode(), UnionImpl.class));
        p.add(Values.class, new SimpleImplementation(SP.Values.asNode(), ValuesImpl.class));
        p.add(Variable.class, new SimpleImplementation(SP.Variable.asNode(), VariableImpl.class));
        return p;
    }
}
