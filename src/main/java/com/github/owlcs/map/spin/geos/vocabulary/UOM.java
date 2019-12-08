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

package com.github.owlcs.map.spin.geos.vocabulary;

import com.github.owlcs.ontapi.jena.utils.Iter;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Units of Measure (Open Geospatial Consortium)
 */
@SuppressWarnings("WeakerAccess")
public class UOM {
    public static final String PREFIX = "uom";
    public static final String BASE_URI = "http://www.opengis.net/def/uom/OGC/1.0";
    public static final String NS = BASE_URI + "/";

    public static String getURI() {
        return NS;
    }

    // angular
    public static final Resource radian = resource("radian");
    public static final Resource microRadian = resource("microRadian");
    public static final Resource degree = resource("degree");
    public static final Resource minute = resource("minute");
    public static final Resource second = resource("second");
    public static final Resource grad = resource("grad");

    // linear (SI)
    public static final Resource metre = resource("metre");
    public static final Resource meter = resource("meter");
    public static final Resource kilometer = resource("kilometer");
    public static final Resource kilometre = resource("kilometre");
    public static final Resource centimetre = resource("centimetre");
    public static final Resource centimeter = resource("centimeter");
    public static final Resource millimetre = resource("millimetre");
    public static final Resource millimeter = resource("millimeter");

    // linear (non-SI)
    public static final Resource mile = resource("mile");
    public static final Resource statuteMile = resource("statuteMile");
    public static final Resource yard = resource("yard");
    public static final Resource foot = resource("foot");
    public static final Resource inch = resource("inch");
    public static final Resource nauticalMile = resource("nauticalMile");
    public static final Resource surveyFootUS = resource("surveyFootUS");

    protected static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    public static Set<Resource> getAngularUOMs() {
        return setOf(radian, microRadian, degree, minute, second, grad);
    }

    public static Set<Resource> getSILinearUOMs() {
        return setOf(meter, metre, kilometer, kilometre, centimeter, centimetre, millimeter, millimetre);
    }

    public static Set<Resource> getNonSILinearUOMs() {
        return setOf(mile, statuteMile, yard, foot, inch, nauticalMile, surveyFootUS);
    }

    public static Set<Resource> getAllUOMs() {
        Set<Resource> res = new HashSet<>();
        res.addAll(getAngularUOMs());
        res.addAll(getSILinearUOMs());
        res.addAll(getNonSILinearUOMs());
        res.addAll(URN.getAngularUOMs());
        res.addAll(URN.getLinearUOMs());
        return res;
    }

    @SafeVarargs
    private static <X> Set<X> setOf(X... resources) {
        return Arrays.stream(resources).collect(Iter.toUnmodifiableSet());
    }

    /**
     * @see <a href='https://sis.apache.org/apidocs/org/apache/sis/measure/Units.html'>org.apache.sis.measure.Units</a>
     */
    public static class URN {
        public static final String NS = "urn:ogc:def:uom:EPSG::";

        // angular
        public static final Resource radian = resource(9101);
        public static final Resource microRadian = resource(9109);
        public static final Resource degree = resource(9102);
        public static final Resource minute = resource(9103);
        public static final Resource second = resource(9104);
        public static final Resource grad = resource(9105);

        // linear
        public static final Resource metre = resource(9001);
        public static final Resource kilometer = resource(9036);
        public static final Resource centimeter = resource(1033);
        public static final Resource millimeter = resource(1025);
        public static final Resource statuteMile = resource(9093);
        public static final Resource foot = resource(9002);
        public static final Resource yard = resource(9096);
        public static final Resource nauticalMile = resource(9030);
        public static final Resource surveyFootUS = resource(9003);

        protected static Resource resource(int local) {
            return ResourceFactory.createResource(NS + local);
        }

        public static Set<Resource> getAngularUOMs() {
            return setOf(radian, microRadian, degree, minute, second, grad);
        }

        public static Set<Resource> getLinearUOMs() {
            return setOf(meter, metre, kilometer, centimeter, millimeter, statuteMile, foot, yard, nauticalMile, surveyFootUS);
        }
    }
}
