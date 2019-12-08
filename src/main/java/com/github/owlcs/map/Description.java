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

package com.github.owlcs.map;

/**
 * A package-local common interface to provide description based on {@code rdfs:comment} and {@code rdfs:label}.
 * <p>
 * Created by @szuev on 10.04.2018.
 */
interface Description {

    /**
     * Returns a {@code rdfs:comment} concatenated for the specified lang with symbol '\n'.
     *
     * @param lang String or null to get default
     * @return String comment (or empty string if no {@code rdfs:comment})
     */
    String getComment(String lang);

    /**
     * Returns a {@code rdfs:label} concatenated for the specified lang with symbol '\n'.
     *
     * @param lang String or null to get default
     * @return String label (or empty string if no {@code rdfs:label})
     */
    String getLabel(String lang);

    /**
     * Returns default (no lang) merged comment.
     *
     * @return String
     */
    default String getComment() {
        return getComment(null);
    }

    /**
     * Returns default (no lang) merged label.
     *
     * @return String
     */
    default String getLabel() {
        return getLabel(null);
    }

}
