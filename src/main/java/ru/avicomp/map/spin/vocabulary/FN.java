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

package ru.avicomp.map.spin.vocabulary;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The vocabulary which describes https://www.w3.org/2005/xpath-functions/.
 * It is already defined in functions-fn.ttl, but not all functions are included.
 * <p>
 * Created by @szuev on 09.06.2018.
 *
 * @see org.apache.jena.sparql.function.StandardFunctions
 * @see org.apache.jena.sparql.ARQConstants#fnPrefix
 * @see <a href='http://www.w3.org/2005/xpath-functions/'>XQuery, XPath, and XSLT Functions and Operators Namespace Document</a>
 */
public class FN {
    public static final String BASE_URI = "http://www.w3.org/2005/xpath-functions";
    public static final String URI = BASE_URI + "/";
    public static final String NS = BASE_URI + "#";

    // from Composer's functions-fn.ttl
    public static final Resource round = resource("round");
    // from avc.fn.ttl
    public static final Resource format_number = resource("format-number");
    // following functions are waiting to be added to the avc.fn.ttl library:
    public static final Resource round_half_to_even = resource("round-half-to-even");
    public static final Resource collation_key = resource("collation-key");
    public static final Resource encode_for_uri = resource("encode-for-uri");
    public static final Resource normalize_space = resource("normalize-space");
    public static final Resource normalize_unicode = resource("normalize-unicode");
    public static final Resource substring_after = resource("substring-after");
    public static final Resource substring_before = resource("substring-before");
    public static final Resource replace = resource("replace");
    public static final Resource timezone_from_dateTime = resource("timezone-from-dateTime");
    public static final Resource year_from_dateTime = resource("year-from-dateTime");
    public static final Resource years_from_duration = resource("years-from-duration");
    public static final Resource seconds_from_dateTime = resource("seconds-from-dateTime");
    public static final Resource seconds_from_duration = resource("seconds-from-duration");
    public static final Resource minutes_from_dateTime = resource("minutes-from-dateTime");
    public static final Resource minutes_from_duration = resource("minutes-from-duration");
    public static final Resource month_from_dateTime = resource("month-from-dateTime");
    public static final Resource months_from_duration = resource("months-from-duration");
    public static final Resource hours_from_dateTime = resource("hours-from-dateTime");
    public static final Resource hours_from_duration = resource("hours-from-duration");
    public static final Resource dateTime = resource("dateTime");
    public static final Resource day_from_dateTime = resource("day-from-dateTime");
    public static final Resource days_from_duration = resource("days-from-duration");
    public static final Resource adjust_date_to_timezone = resource("adjust-date-to-timezone");
    public static final Resource adjust_dateTime_to_timezone = resource("adjust-dateTime-to-timezone");
    public static final Resource adjust_time_to_timezone = resource("adjust-time-to-timezone");

    public static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }
}
