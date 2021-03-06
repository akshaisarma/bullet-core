/*
 *  Copyright 2017, Yahoo Inc.
 *  Licensed under the terms of the Apache License, Version 2.0.
 *  See the LICENSE file associated with the project for terms.
 */
package com.yahoo.bullet.parsing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yahoo.bullet.common.BulletConfig;

public class Parser {
    private static final FieldTypeAdapterFactory<Clause> CLAUSE_FACTORY =
            FieldTypeAdapterFactory.of(Clause.class, t -> t.getAsJsonObject().get(Clause.OPERATION_FIELD).getAsString())
                                   .registerSubType(FilterClause.class, Clause.Operation.RELATIONALS)
                                   .registerSubType(LogicalClause.class, Clause.Operation.LOGICALS);
    private static final Gson GSON = new GsonBuilder().registerTypeAdapterFactory(CLAUSE_FACTORY)
                                                      .excludeFieldsWithoutExposeAnnotation()
                                                      .create();

    /**
     * Parses a Query out of the query string.
     *
     * @param queryString The String version of the query.
     * @param config Additional configuration for the query.
     *
     * @return The parsed, configured Query.
     * @throws com.google.gson.JsonParseException if there was an issue parsing the query.
     */
    public static Query parse(String queryString, BulletConfig config) {
        Query query = GSON.fromJson(queryString, Query.class);
        query.configure(config);
        return query;
    }
}

