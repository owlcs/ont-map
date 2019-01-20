/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.avicomp.map.spin;

import org.apache.jena.enhanced.Personality;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.model.impl.*;
import org.topbraid.spin.model.update.impl.*;
import org.topbraid.spin.util.SimpleImplementation;
import org.topbraid.spin.util.SimpleImplementation2;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.impl.conf.PersonalityBuilder;

/**
 * Settings and personalities for a {@link Model jena model} which contain spin rules and other stuff.
 * Created by @szuev on 07.04.2018.
 *
 * @see OntModelConfig
 */
@SuppressWarnings({"WeakerAccess"})
public class SpinModelConfig {
    // OWL2 personality (lax version)
    public static final OntPersonality ONT_PERSONALITY = OntModelConfig.ONT_PERSONALITY_LAX.copy();
    // SPIN personality
    public static Personality<RDFNode> LIB_PERSONALITY = init(OntModelConfig.getStandardPersonality());
    // OWL2 + SPIN personality
    public static final OntPersonality ONT_LIB_PERSONALITY = PersonalityBuilder.from(ONT_PERSONALITY)
            .addPersonality(LIB_PERSONALITY).build();

    /**
     * See org.topbraid.spin.vocabulary.SP#init(Personality)
     *
     * @param p {@link Personality} to modify
     * @return {@link Personality} the same instance
     */
    @SuppressWarnings("deprecation")
    public static Personality<RDFNode> init(Personality<RDFNode> p) {
        p.add(org.topbraid.spin.model.Aggregation.class, new SimpleImplementation(SPL.Argument.asNode(), AggregationImpl.class));
        p.add(org.topbraid.spin.model.Argument.class, new SimpleImplementation(SPL.Argument.asNode(), ArgumentImpl.class));
        p.add(org.topbraid.spin.model.Attribute.class, new SimpleImplementation(SPL.Attribute.asNode(), AttributeImpl.class));
        p.add(org.topbraid.spin.model.Ask.class, new SimpleImplementation(SP.Ask.asNode(), AskImpl.class));
        p.add(org.topbraid.spin.model.Bind.class, new SimpleImplementation2(SP.Bind.asNode(), SP.Let.asNode(), BindImpl.class));
        p.add(org.topbraid.spin.model.update.Clear.class, new SimpleImplementation(SP.Clear.asNode(), ClearImpl.class));
        p.add(org.topbraid.spin.model.Construct.class, new SimpleImplementation(SP.Construct.asNode(), ConstructImpl.class));
        p.add(org.topbraid.spin.model.update.Create.class, new SimpleImplementation(SP.Create.asNode(), CreateImpl.class));
        p.add(org.topbraid.spin.model.update.Delete.class, new SimpleImplementation(SP.Delete.asNode(), DeleteImpl.class));
        p.add(org.topbraid.spin.model.update.DeleteData.class, new SimpleImplementation(SP.DeleteData.asNode(), DeleteDataImpl.class));
        p.add(org.topbraid.spin.model.update.DeleteWhere.class, new SimpleImplementation(SP.DeleteWhere.asNode(), DeleteWhereImpl.class));
        p.add(org.topbraid.spin.model.Describe.class, new SimpleImplementation(SP.Describe.asNode(), DescribeImpl.class));
        p.add(org.topbraid.spin.model.update.Drop.class, new SimpleImplementation(SP.Drop.asNode(), DropImpl.class));
        p.add(org.topbraid.spin.model.ElementList.class, new SimpleImplementation(RDF.List.asNode(), ElementListImpl.class));
        p.add(org.topbraid.spin.model.Exists.class, new SimpleImplementation(SP.Exists.asNode(), ExistsImpl.class));
        p.add(org.topbraid.spin.model.Function.class, new SimpleImplementation(SPIN.Function.asNode(), FunctionImpl.class));
        p.add(org.topbraid.spin.model.FunctionCall.class, new SimpleImplementation(SPIN.Function.asNode(), FunctionCallImpl.class));
        p.add(org.topbraid.spin.model.Filter.class, new SimpleImplementation(SP.Filter.asNode(), FilterImpl.class));
        p.add(org.topbraid.spin.model.update.Insert.class, new SimpleImplementation(SP.Insert.asNode(), InsertImpl.class));
        p.add(org.topbraid.spin.model.update.InsertData.class, new SimpleImplementation(SP.InsertData.asNode(), InsertDataImpl.class));
        p.add(org.topbraid.spin.model.update.Load.class, new SimpleImplementation(SP.Load.asNode(), LoadImpl.class));
        p.add(org.topbraid.spin.model.Minus.class, new SimpleImplementation(SP.Minus.asNode(), MinusImpl.class));
        p.add(org.topbraid.spin.model.update.Modify.class, new SimpleImplementation(SP.Modify.asNode(), ModifyImpl.class));
        p.add(org.topbraid.spin.model.Module.class, new SimpleImplementation(SPIN.Module.asNode(), ModuleImpl.class));
        p.add(org.topbraid.spin.model.NamedGraph.class, new SimpleImplementation(SP.NamedGraph.asNode(), NamedGraphImpl.class));
        p.add(org.topbraid.spin.model.NotExists.class, new SimpleImplementation(SP.NotExists.asNode(), NotExistsImpl.class));
        p.add(org.topbraid.spin.model.Optional.class, new SimpleImplementation(SP.Optional.asNode(), OptionalImpl.class));
        p.add(org.topbraid.spin.model.Service.class, new SimpleImplementation(SP.Service.asNode(), ServiceImpl.class));
        p.add(org.topbraid.spin.model.Select.class, new SimpleImplementation(SP.Select.asNode(), SelectImpl.class));
        p.add(org.topbraid.spin.model.SubQuery.class, new SimpleImplementation(SP.SubQuery.asNode(), SubQueryImpl.class));
        p.add(org.topbraid.spin.model.SPINInstance.class, new SimpleImplementation(RDFS.Resource.asNode(), SPINInstanceImpl.class));
        p.add(org.topbraid.spin.model.Template.class, new SimpleImplementation(SPIN.Template.asNode(), TemplateImpl.class));
        p.add(org.topbraid.spin.model.TemplateCall.class, new SimpleImplementation(RDFS.Resource.asNode(), TemplateCallImpl.class));
        p.add(org.topbraid.spin.model.TriplePath.class, new SimpleImplementation(SP.TriplePath.asNode(), TriplePathImpl.class));
        p.add(org.topbraid.spin.model.TriplePattern.class, new SimpleImplementation(SP.TriplePattern.asNode(), TriplePatternImpl.class));
        p.add(org.topbraid.spin.model.TripleTemplate.class, new SimpleImplementation(SP.TripleTemplate.asNode(), TripleTemplateImpl.class));
        p.add(org.topbraid.spin.model.Union.class, new SimpleImplementation(SP.Union.asNode(), UnionImpl.class));
        p.add(org.topbraid.spin.model.Values.class, new SimpleImplementation(SP.Values.asNode(), ValuesImpl.class));
        p.add(org.topbraid.spin.model.Variable.class, new SimpleImplementation(SP.Variable.asNode(), VariableImpl.class));
        return p;
    }

    public static Model createSpinModel(Graph graph) {
        return createStandardModel(graph, LIB_PERSONALITY);
    }

    public static Model createStandardModel(Graph graph, Personality<RDFNode> personality) {
        return new ModelCom(graph, personality);
    }
}
