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

package ru.avicomp.map;

import org.apache.jena.shared.JenaException;

/**
 * A base map exception.
 * <p>
 * Created by @szuev on 06.04.2018.
 */
public class MapJenaException extends JenaException {

    public MapJenaException() {
    }

    public MapJenaException(String message) {
        super(message);
    }

    public MapJenaException(Throwable cause) {
        super(cause);
    }

    public MapJenaException(String message, Throwable cause) {
        super(message, cause);
    }

    public static <T> T notNull(T obj, String message) {
        if (obj == null)
            throw message == null ? new MapJenaException() : new MapJenaException(message);
        return obj;
    }

    public static class Unsupported extends MapJenaException {
        public Unsupported() {
            super();
        }

        public Unsupported(String message) {
            super(message);
        }
    }
}
