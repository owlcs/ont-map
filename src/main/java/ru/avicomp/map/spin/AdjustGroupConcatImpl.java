/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, The University of Manchester, owl.cs group.
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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An implementation to adjust {@code avc:groupConcat} function body.
 * Created by @szuev on 21.05.2018.
 *
 * @see ru.avicomp.map.spin.vocabulary.AVC#groupConcat
 */
public class AdjustGroupConcatImpl implements AdjustFunctionBody {

    /**
     * Injects separator directly to group-concat function.
     *
     * @param model {@link Model}
     * @param call  {@link ru.avicomp.map.MapFunction.Call}
     * @return boolean
     * @throws MapJenaException in case something is wrong
     */
    @Override
    public Boolean apply(Model model, MapFunction.Call call) throws MapJenaException {
        MapFunction function = MapJenaException.notNull(call, "Null function call specified").getFunction();
        Resource resource = MapJenaException.notNull(model, "Null model specified").getResource(function.name());
        MapFunction.Arg arg = function.getArg(SPINMAPL.separator);
        Object value = call.get(arg);
        if (!(value instanceof String)) {
            throw new MapJenaException.IllegalState("Null or wrong value for separator");
        }
        String separator = (String) value;
        List<Statement> prev = SpinModels.getLocalFunctionBody(model, resource)
                .stream()
                .filter(s -> Objects.equals(s.getPredicate(), SP.separator))
                .filter(s -> s.getObject().isLiteral())
                .collect(Collectors.toList());
        if (prev.size() != 1)
            throw new MapJenaException.IllegalState("Expected single sp:separator literal inside expression");
        Statement s = prev.get(0);
        if (separator.equals(s.getString())) {
            return false;
        }
        model.add(s.getSubject(), s.getPredicate(), separator).remove(s);
        return true;
    }
}
