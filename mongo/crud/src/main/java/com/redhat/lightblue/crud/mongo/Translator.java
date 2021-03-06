/*
 Copyright 2013 Red Hat, Inc. and/or its affiliates.

 This file is part of lightblue.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.redhat.lightblue.crud.mongo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.redhat.lightblue.crud.MetadataResolver;
import com.redhat.lightblue.metadata.ArrayElement;
import com.redhat.lightblue.metadata.ArrayField;
import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.metadata.FieldCursor;
import com.redhat.lightblue.metadata.FieldTreeNode;
import com.redhat.lightblue.metadata.ObjectArrayElement;
import com.redhat.lightblue.metadata.ObjectField;
import com.redhat.lightblue.metadata.ReferenceField;
import com.redhat.lightblue.metadata.SimpleArrayElement;
import com.redhat.lightblue.metadata.SimpleField;
import com.redhat.lightblue.metadata.Type;
import com.redhat.lightblue.query.ArrayContainsExpression;
import com.redhat.lightblue.query.ArrayMatchExpression;
import com.redhat.lightblue.query.BinaryComparisonOperator;
import com.redhat.lightblue.query.CompositeSortKey;
import com.redhat.lightblue.query.FieldComparisonExpression;
import com.redhat.lightblue.query.NaryLogicalExpression;
import com.redhat.lightblue.query.NaryLogicalOperator;
import com.redhat.lightblue.query.NaryRelationalExpression;
import com.redhat.lightblue.query.NaryRelationalOperator;
import com.redhat.lightblue.query.QueryExpression;
import com.redhat.lightblue.query.RegexMatchExpression;
import com.redhat.lightblue.query.Sort;
import com.redhat.lightblue.query.SortKey;
import com.redhat.lightblue.query.UnaryLogicalExpression;
import com.redhat.lightblue.query.UnaryLogicalOperator;
import com.redhat.lightblue.query.Value;
import com.redhat.lightblue.query.ValueComparisonExpression;
import com.redhat.lightblue.query.UpdateExpression;
import com.redhat.lightblue.query.UpdateExpressionList;
import com.redhat.lightblue.query.ArrayUpdateExpression;
import com.redhat.lightblue.query.FieldAndRValue;
import com.redhat.lightblue.query.RValueExpression;
import com.redhat.lightblue.query.PrimitiveUpdateExpression;
import com.redhat.lightblue.query.PartialUpdateExpression;
import com.redhat.lightblue.query.SetExpression;
import com.redhat.lightblue.query.UnsetExpression;
import com.redhat.lightblue.util.Error;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.JsonNodeCursor;
import com.redhat.lightblue.util.Path;

/**
 * Translations between BSON and JSON. This class is thread-safe, and can be shared between threads
 */
public class Translator {

    public static final String OBJECT_TYPE_STR = "object_type";
    public static final Path OBJECT_TYPE = new Path(OBJECT_TYPE_STR);

    public static final Path ID_PATH = new Path("_id");

    public static final String ERR_NO_OBJECT_TYPE = "NO_OBJECT_TYPE";
    public static final String ERR_INVALID_OBJECTTYPE = "INVALID_OBJECTTYPE";
    public static final String ERR_INVALID_FIELD = "INVALID_FIELD";
    public static final String ERR_INVALID_COMPARISON = "INVALID_COMPARISON";

    private static final Logger LOGGER = LoggerFactory.getLogger(Translator.class);

    private final MetadataResolver mdResolver;
    private final JsonNodeFactory factory;

    private static final Map<BinaryComparisonOperator, String> BINARY_COMPARISON_OPERATOR_JS_MAP;
    private static final Map<BinaryComparisonOperator, String> BINARY_COMPARISON_OPERATOR_MAP;
    private static final Map<NaryLogicalOperator, String> NARY_LOGICAL_OPERATOR_MAP;
    private static final Map<UnaryLogicalOperator, String> UNARY_LOGICAL_OPERATOR_MAP;
    private static final Map<NaryRelationalOperator, String> NARY_RELATIONAL_OPERATOR_MAP;

    static {
        BINARY_COMPARISON_OPERATOR_JS_MAP = new HashMap<>();
        BINARY_COMPARISON_OPERATOR_JS_MAP.put(BinaryComparisonOperator._eq, "==");
        BINARY_COMPARISON_OPERATOR_JS_MAP.put(BinaryComparisonOperator._neq, "!=");
        BINARY_COMPARISON_OPERATOR_JS_MAP.put(BinaryComparisonOperator._lt, "<");
        BINARY_COMPARISON_OPERATOR_JS_MAP.put(BinaryComparisonOperator._gt, ">");
        BINARY_COMPARISON_OPERATOR_JS_MAP.put(BinaryComparisonOperator._lte, "<=");
        BINARY_COMPARISON_OPERATOR_JS_MAP.put(BinaryComparisonOperator._gte, ">=");

        BINARY_COMPARISON_OPERATOR_MAP = new HashMap<>();
        BINARY_COMPARISON_OPERATOR_MAP.put(BinaryComparisonOperator._eq, "$eq");
        BINARY_COMPARISON_OPERATOR_MAP.put(BinaryComparisonOperator._neq, "$neq");
        BINARY_COMPARISON_OPERATOR_MAP.put(BinaryComparisonOperator._lt, "$lt");
        BINARY_COMPARISON_OPERATOR_MAP.put(BinaryComparisonOperator._gt, "$gt");
        BINARY_COMPARISON_OPERATOR_MAP.put(BinaryComparisonOperator._lte, "$lte");
        BINARY_COMPARISON_OPERATOR_MAP.put(BinaryComparisonOperator._gte, "$gte");

        NARY_LOGICAL_OPERATOR_MAP = new HashMap<>();
        NARY_LOGICAL_OPERATOR_MAP.put(NaryLogicalOperator._and, "$and");
        NARY_LOGICAL_OPERATOR_MAP.put(NaryLogicalOperator._or, "$or");

        UNARY_LOGICAL_OPERATOR_MAP = new HashMap<>();
        UNARY_LOGICAL_OPERATOR_MAP.put(UnaryLogicalOperator._not, "$not");

        NARY_RELATIONAL_OPERATOR_MAP = new HashMap<>();
        NARY_RELATIONAL_OPERATOR_MAP.put(NaryRelationalOperator._in, "$in");
        NARY_RELATIONAL_OPERATOR_MAP.put(NaryRelationalOperator._not_in, "$nin");
    }

    /**
     * Constructs a translator using the given metadata resolver and factory
     */
    public Translator(MetadataResolver mdResolver,
                      JsonNodeFactory factory) {
        this.mdResolver = mdResolver;
        this.factory = factory;
    }

    /**
     * Translates a list of JSON documents to DBObjects. Translation is metadata driven.
     */
    public DBObject[] toBson(List<? extends JsonDoc> docs) {
        DBObject[] ret = new DBObject[docs.size()];
        int i = 0;
        for (JsonDoc doc : docs) {
            ret[i++] = toBson(doc);
        }
        return ret;
    }

    /**
     * Translates a JSON document to DBObject. Translation is metadata driven.
     */
    public DBObject toBson(JsonDoc doc) {
        LOGGER.debug("toBson() enter");
        JsonNode node = doc.get(OBJECT_TYPE);
        if (node == null) {
            throw Error.get(ERR_NO_OBJECT_TYPE);
        }
        EntityMetadata md = mdResolver.getEntityMetadata(node.asText());
        if (md == null) {
            throw Error.get(ERR_INVALID_OBJECTTYPE, node.asText());
        }
        DBObject ret = toBson(doc, md);
        LOGGER.debug("toBson() return");
        return ret;
    }

    /**
     * Traslates a DBObject document to Json document
     */
    public JsonDoc toJson(DBObject object) {
        LOGGER.debug("toJson() enter");
        Object type = object.get(OBJECT_TYPE_STR);
        if (type == null) {
            throw Error.get(ERR_NO_OBJECT_TYPE);
        }
        EntityMetadata md = mdResolver.getEntityMetadata(type.toString());
        if (md == null) {
            throw Error.get(ERR_INVALID_OBJECTTYPE, type.toString());
        }
        JsonDoc doc = toJson(object, md);
        LOGGER.debug("toJson() return");
        return doc;
    }

    /**
     * Translates DBObjects into Json documents
     */
    public List<JsonDoc> toJson(List<DBObject> objects) {
        List<JsonDoc> list = new ArrayList<>(objects.size());
        for (DBObject object : objects) {
            list.add(toJson(object));
        }
        return list;
    }

    /**
     * Translates a sort expression to Mongo sort expression
     */
    public DBObject translate(Sort sort) {
        LOGGER.debug("translate {}", sort);
        Error.push("translateSort");
        DBObject ret;
        try {
            if (sort instanceof CompositeSortKey) {
                ret = translateCompositeSortKey((CompositeSortKey) sort);
            } else {
                ret = translateSortKey((SortKey) sort);
            }
        } finally {
            Error.pop();
        }
        return new BasicDBObject("$sort", ret);
    }

    /**
     * Translates a query to Mongo query
     *
     * @param md Entity metadata
     * @param query The query expression
     */
    public DBObject translate(EntityMetadata md, QueryExpression query) {
        LOGGER.debug("translate {}", query);
        Error.push("translateQuery");
        FieldTreeNode mdRoot = md.getFieldTreeRoot();
        try {
            return translate(mdRoot, query);
        } finally {
            Error.pop();
        }
    }

    /**
     * Tranlates an update expression to Mongo query
     *
     * @param md Entity metedata
     * @param expr Update expression
     *
     * If the update expresssion is something that can be translated into a mongo update expression, translation is
     * performed. Otherwise, CannotTranslateException is thrown, and the update operation must be performed using the
     * Updaters.
     */
    public DBObject translate(EntityMetadata md, UpdateExpression expr)
            throws CannotTranslateException {
        LOGGER.debug("translate {}", expr);
        Error.push("translateUpdate");
        try {
            BasicDBObject ret = new BasicDBObject();
            translateUpdate(md.getFieldTreeRoot(), expr, ret);
            LOGGER.debug("translated={}", ret);
            return ret;
        } finally {
            Error.pop();
        }
    }

    /**
     * Translate update expression list and primitive updates. Anything else causes an exception.
     */
    private void translateUpdate(FieldTreeNode root, UpdateExpression expr, BasicDBObject dest)
            throws CannotTranslateException {
        if (expr instanceof ArrayUpdateExpression) {
            throw new CannotTranslateException(expr);
        } else if (expr instanceof PrimitiveUpdateExpression) {
            translatePrimitiveUpdate(root, (PrimitiveUpdateExpression) expr, dest);
        } else if (expr instanceof UpdateExpressionList) {
            for (PartialUpdateExpression x : ((UpdateExpressionList) expr).getList()) {
                translateUpdate(root, x, dest);
            }
        }
    }

    /**
     * Attempt to translate a primitive update expression. If the epxression touches any arrays or array elements,
     * translation fails.
     */
    private void translatePrimitiveUpdate(FieldTreeNode root,
                                          PrimitiveUpdateExpression expr,
                                          BasicDBObject dest)
            throws CannotTranslateException {
        if (expr instanceof SetExpression) {
            translateSet(root, (SetExpression) expr, dest);
        } else if (expr instanceof UnsetExpression) {
            translateUnset(root, (UnsetExpression) expr, dest);
        } else {
            throw new CannotTranslateException(expr);
        }
    }

    private void translateSet(FieldTreeNode root,
                              SetExpression expr,
                              BasicDBObject dest)
            throws CannotTranslateException {
        String op;
        switch (expr.getOp()) {
            case _set:
                op = "$set";
                break;
            case _add:
                op = "$inc";
                break;
            default:
                throw new CannotTranslateException(expr);
        }
        BasicDBObject obj = (BasicDBObject) dest.get(op);
        if (obj == null) {
            obj = new BasicDBObject();
            dest.put(op, obj);
        }
        for (FieldAndRValue frv : expr.getFields()) {
            Path field = frv.getField();
            if (hasArray(root, field)) {
                throw new CannotTranslateException(expr);
            }
            RValueExpression rvalue = frv.getRValue();
            if (rvalue.getType() == RValueExpression.RValueType._value) {
                Value value = rvalue.getValue();
                FieldTreeNode ftn = root.resolve(field);
                if (ftn == null) {
                    throw new CannotTranslateException(expr);
                }
                if (!(ftn instanceof SimpleField)) {
                    throw new CannotTranslateException(expr);
                }
                Object valueObject = ftn.getType().cast(value.getValue());
                if (field.equals(ID_PATH)) {
                    valueObject = new ObjectId(valueObject.toString());
                }
                obj.put(field.toString(), valueObject);
            } else {
                throw new CannotTranslateException(expr);
            }
        }
    }

    private void translateUnset(FieldTreeNode root,
                                UnsetExpression expr,
                                BasicDBObject dest)
            throws CannotTranslateException {
        BasicDBObject obj = (BasicDBObject) dest.get("$unset");
        if (obj == null) {
            obj = new BasicDBObject();
            dest.put("$unset", obj);
        }
        for (Path field : expr.getFields()) {
            if (hasArray(root, field)) {
                throw new CannotTranslateException(expr);
            }
            obj.put(field.toString(), "");
        }
    }

    /**
     * Returns true if the field is an array, or points to a field within an array
     */
    private boolean hasArray(FieldTreeNode root, Path field)
            throws CannotTranslateException {
        FieldTreeNode node = root.resolve(field);
        if (node == null) {
            throw new CannotTranslateException(field);
        }
        do {
            if (node instanceof ArrayField
                    || node instanceof ArrayElement) {
                return true;
            } else {
                node = node.getParent();
            }
        } while (node != null);
        return false;
    }

    private DBObject translateSortKey(SortKey sort) {
        return new BasicDBObject(sort.getField().toString(), sort.isDesc() ? -1 : 1);
    }

    private DBObject translateCompositeSortKey(CompositeSortKey sort) {
        DBObject ret = null;
        for (SortKey key : sort.getKeys()) {
            if (ret == null) {
                ret = translateSortKey(key);
            } else {
                ret.put(key.getField().toString(), key.isDesc() ? -1 : 1);
            }
        }
        return ret;
    }

    private DBObject translate(FieldTreeNode context, QueryExpression query) {
        DBObject ret;
        if (query instanceof ArrayContainsExpression) {
            ret = translateArrayContains(context, (ArrayContainsExpression) query);
        } else if (query instanceof ArrayMatchExpression) {
            ret = translateArrayElemMatch(context, (ArrayMatchExpression) query);
        } else if (query instanceof FieldComparisonExpression) {
            ret = translateFieldComparison((FieldComparisonExpression) query);
        } else if (query instanceof NaryLogicalExpression) {
            ret = translateNaryLogicalExpression(context, (NaryLogicalExpression) query);
        } else if (query instanceof NaryRelationalExpression) {
            ret = translateNaryRelationalExpression(context, (NaryRelationalExpression) query);
        } else if (query instanceof RegexMatchExpression) {
            ret = translateRegexMatchExpression((RegexMatchExpression) query);
        } else if (query instanceof UnaryLogicalExpression) {
            ret = translateUnaryLogicalExpression(context, (UnaryLogicalExpression) query);
        } else {
            ret = translateValueComparisonExpression(context, (ValueComparisonExpression) query);
        }
        return ret;
    }

    private FieldTreeNode resolve(FieldTreeNode context, Path field) {
        FieldTreeNode node = context.resolve(field);
        if (node == null) {
            throw Error.get(ERR_INVALID_FIELD, field.toString());
        }
        return node;
    }

    /**
     * Converts a value list to a list of values with the proper type
     */
    private List<Object> translateValueList(Type t, List<Value> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(MongoCrudConstants.ERR_EMPTY_VALUE_LIST);
        }
        List<Object> ret = new ArrayList<>(values.size());
        for (Value v : values) {
            Object value = v == null ? null : v.getValue();
            if (value != null) {
                value = t.cast(value);
            }
            ret.add(value);
        }
        return ret;
    }

    private DBObject translateValueComparisonExpression(FieldTreeNode context, ValueComparisonExpression expr) {
        Type t = resolve(context, expr.getField()).getType();
        if (expr.getOp() == BinaryComparisonOperator._eq
                || expr.getOp() == BinaryComparisonOperator._neq) {
            if (!t.supportsEq()) {
                throw Error.get(ERR_INVALID_COMPARISON, expr.toString());
            }
        } else {
            if (!t.supportsOrdering()) {
                throw Error.get(ERR_INVALID_COMPARISON, expr.toString());
            }
        }
        Object valueObject = t.cast(expr.getRvalue().getValue());
        if (expr.getField().equals(ID_PATH)) {
            valueObject = new ObjectId(valueObject.toString());
        }
        if (expr.getOp() == BinaryComparisonOperator._eq) {
            return new BasicDBObject(expr.getField().toString(), valueObject);
        } else {
            return new BasicDBObject(expr.getField().toString(),
                    new BasicDBObject(BINARY_COMPARISON_OPERATOR_MAP.get(expr.getOp()), valueObject));
        }
    }

    private DBObject translateRegexMatchExpression(RegexMatchExpression expr) {
        StringBuilder options = new StringBuilder();
        BasicDBObject regex = new BasicDBObject("$regex", expr.getRegex());
        if (expr.isCaseInsensitive()) {
            options.append('i');
        }
        if (expr.isMultiline()) {
            options.append('m');
        }
        if (expr.isExtended()) {
            options.append('x');
        }
        if (expr.isDotAll()) {
            options.append('s');
        }
        String opStr = options.toString();
        if (opStr.length() > 0) {
            regex.append("$options", opStr);
        }
        return new BasicDBObject(expr.getField().toString(), regex);
    }

    private DBObject translateNaryRelationalExpression(FieldTreeNode context, NaryRelationalExpression expr) {
        Type t = resolve(context, expr.getField()).getType();
        if (t.supportsEq()) {
            List<Object> values = translateValueList(t, expr.getValues());
            return new BasicDBObject(expr.getField().toString(),
                    new BasicDBObject(NARY_RELATIONAL_OPERATOR_MAP.get(expr.getOp()),
                            values));
        } else {
            throw Error.get(ERR_INVALID_FIELD, expr.toString());
        }
    }

    private DBObject translateUnaryLogicalExpression(FieldTreeNode context, UnaryLogicalExpression expr) {
        return new BasicDBObject(UNARY_LOGICAL_OPERATOR_MAP.get(expr.getOp()), translate(context, expr.getQuery()));
    }

    private DBObject translateNaryLogicalExpression(FieldTreeNode context, NaryLogicalExpression expr) {
        List<QueryExpression> queries = expr.getQueries();
        List<DBObject> list = new ArrayList<>(queries.size());
        for (QueryExpression query : queries) {
            list.add(translate(context, query));
        }
        return new BasicDBObject(NARY_LOGICAL_OPERATOR_MAP.get(expr.getOp()), list);
    }

    private DBObject translateFieldComparison(FieldComparisonExpression expr) {
        StringBuilder str = new StringBuilder(64);
        str.append("this.").
                append(expr.getField().toString()).
                append(BINARY_COMPARISON_OPERATOR_JS_MAP.get(expr.getOp())).
                append("this.").
                append(expr.getRfield().toString());
        return new BasicDBObject("$where", str.toString());
    }

    private DBObject translateArrayElemMatch(FieldTreeNode context, ArrayMatchExpression expr) {
        FieldTreeNode arrayNode = resolve(context, expr.getArray());
        if (arrayNode instanceof ArrayField) {
            ArrayElement el = ((ArrayField) arrayNode).getElement();
            if (el instanceof ObjectArrayElement) {
                return new BasicDBObject(expr.getArray().toString(),
                        translate(el, expr.getElemMatch()));
            }
        }
        throw Error.get(ERR_INVALID_FIELD, expr.toString());
    }

    private DBObject translateArrayContains(FieldTreeNode context, ArrayContainsExpression expr) {
        DBObject ret = null;
        FieldTreeNode arrayNode = resolve(context, expr.getArray());
        if (arrayNode instanceof ArrayField) {
            Type t = ((ArrayField) arrayNode).getElement().getType();
            switch (expr.getOp()) {
                case _all:
                    ret = translateArrayContainsAll(t, expr.getArray(), expr.getValues());
                    break;
                case _any:
                    ret = translateArrayContainsAny(t, expr.getArray(), expr.getValues());
                    break;
                case _none:
                    ret = translateArrayContainsNone(t, expr.getArray(), expr.getValues());
                    break;
            }
        } else {
            throw Error.get(ERR_INVALID_FIELD, expr.toString());
        }
        return ret;
    }

    /**
     * <pre>
     *   { field : { $all:[values] } }
     * </pre>
     */
    private DBObject translateArrayContainsAll(Type t, Path array, List<Value> values) {
        return new BasicDBObject(array.toString(),
                new BasicDBObject("$all",
                        translateValueList(t, values)));
    }

    /**
     * <pre>
     *     { $or : [ {field:value1},{field:value2},...] }
     * </pre>
     */
    private DBObject translateArrayContainsAny(Type t, Path array, List<Value> values) {
        List<BasicDBObject> l = new ArrayList<>(values.size());
        for (Value x : values) {
            l.add(new BasicDBObject(array.toString(), x == null ? null
                    : x.getValue() == null ? null : t.cast(x.getValue())));
        }
        return new BasicDBObject("$or", l);
    }

    /**
     * <pre>
     * { $not : { $or : [ {field:value1},{field:value2},...]}}
     * </pre>
     */
    private DBObject translateArrayContainsNone(Type t, Path array, List<Value> values) {
        return new BasicDBObject("$not", translateArrayContainsAny(t, array, values));
    }

    private JsonDoc toJson(DBObject object, EntityMetadata md) {
        // Translation is metadata driven. We don't know how to
        // translate something that's not defined in metadata.
        FieldCursor cursor = md.getFieldCursor();
        if (cursor.firstChild()) {
            return new JsonDoc(objectToJson(object, md, cursor));
        } else {
            return null;
        }
    }

    /**
     * Called after firstChild is called on cursor
     */
    private ObjectNode objectToJson(DBObject object, EntityMetadata md, FieldCursor mdCursor) {
        ObjectNode node = factory.objectNode();
        do {
            Path p = mdCursor.getCurrentPath();
            FieldTreeNode field = mdCursor.getCurrentNode();
            String fieldName = field.getName();
            LOGGER.debug("{}", p);
            // Retrieve field value
            Object value = object.get(fieldName);
            if (value != null) {
                if (field instanceof SimpleField) {
                    convertSimpleFieldToJson(node, field, value, fieldName);
                } else if (field instanceof ObjectField) {
                    convertObjectFieldToJson(node, fieldName, md, mdCursor, value, p);
                } else if (field instanceof ArrayField && value instanceof List && mdCursor.firstChild()) {
                    convertArrayFieldToJson(node, fieldName, md, mdCursor, value);
                } else if (field instanceof ReferenceField) {
                    convertReferenceFieldToJson();
                }
            }
        } while (mdCursor.nextSibling());
        return node;
    }

    private void convertSimpleFieldToJson(ObjectNode node, FieldTreeNode field, Object value, String fieldName) {
        JsonNode valueNode = ((SimpleField) field).getType().toJson(factory, value);
        if (valueNode != null) {
            node.set(fieldName, valueNode);
        }
    }

    private void convertObjectFieldToJson(ObjectNode node, String fieldName, EntityMetadata md, FieldCursor mdCursor, Object value, Path p) {
        if (value instanceof DBObject) {
            if (mdCursor.firstChild()) {
                JsonNode valueNode = objectToJson((DBObject) value, md, mdCursor);
                if (valueNode != null) {
                    node.set(fieldName, valueNode);
                }
                mdCursor.parent();
            }
        } else {
            LOGGER.error("Expected DBObject, found {} for {}", value.getClass(), p);
        }
    }

    @SuppressWarnings("rawtypes")
    private void convertArrayFieldToJson(ObjectNode node, String fieldName, EntityMetadata md, FieldCursor mdCursor, Object value) {
        ArrayNode valueNode = factory.arrayNode();
        node.set(fieldName, valueNode);
        // We must have an array element here
        FieldTreeNode x = mdCursor.getCurrentNode();
        if (x instanceof ArrayElement) {
            for (Object item : (List) value) {
                valueNode.add(arrayElementToJson(item, (ArrayElement) x, md, mdCursor));
            }
        }
        mdCursor.parent();
    }

    private void convertReferenceFieldToJson() {
        //TODO
        LOGGER.debug("Converting reference field: ");
    }

    private JsonNode arrayElementToJson(Object value,
                                        ArrayElement el,
                                        EntityMetadata md,
                                        FieldCursor mdCursor) {
        JsonNode ret = null;
        if (el instanceof SimpleArrayElement) {
            if (value != null) {
                ret = ((SimpleArrayElement) el).getType().toJson(factory, value);
            }
        } else {
            if (value != null) {
                if (value instanceof DBObject) {
                    if (mdCursor.firstChild()) {
                        ret = objectToJson((DBObject) value, md, mdCursor);
                        mdCursor.parent();
                    }
                } else {
                    LOGGER.error("Expected DBObject, got {}", value.getClass().getName());
                }
            }
        }
        return ret;
    }

    private BasicDBObject toBson(JsonDoc doc, EntityMetadata md) {
        LOGGER.debug("Entity: {}", md.getName());
        BasicDBObject ret = null;
        JsonNodeCursor cursor = doc.cursor();
        if (cursor.firstChild()) {
            ret = objectToBson(cursor, md);
        }
        return ret;
    }

    private Object toValue(Type t, JsonNode node) {
        if (node == null || node instanceof NullNode) {
            return null;
        } else {
            return t.fromJson(node);
        }
    }

    private void toBson(BasicDBObject dest,
                        SimpleField fieldMd,
                        Path path,
                        JsonNode node) {
        Object value = toValue(fieldMd.getType(), node);
        // Should we add fields with null values to the bson doc? 
        if (value != null) {
            LOGGER.debug("{} = {}", path, value);
            if (path.equals(ID_PATH)) {
                value = new ObjectId(value.toString());
            }
            // Store big values as string. Mongo does not support big values
            if (value instanceof BigDecimal || value instanceof BigInteger) {
                value = value.toString();
            }

            dest.append(path.tail(0), value);
        }
    }

    /**
     * @param cursor The cursor, pointing to the first element of the object
     */
    private BasicDBObject objectToBson(JsonNodeCursor cursor, EntityMetadata md) {
        BasicDBObject ret = new BasicDBObject();
        do {
            Path path = cursor.getCurrentPath();
            JsonNode node = cursor.getCurrentNode();
            LOGGER.debug("field: {}", path);
            FieldTreeNode fieldMdNode = md.resolve(path);
            if (fieldMdNode == null) {
                throw Error.get(ERR_INVALID_FIELD, path.toString());
            }

            if (fieldMdNode instanceof SimpleField) {
                toBson(ret, (SimpleField) fieldMdNode, path, node);
            } else if (fieldMdNode instanceof ObjectField) {
                convertObjectFieldToBson(node, cursor, ret, path, md);
            } else if (fieldMdNode instanceof ArrayField) {
                convertArrayFieldToBson(node, cursor, ret, fieldMdNode, path, md);
            } else if (fieldMdNode instanceof ReferenceField) {
                convertReferenceFieldToBson();
            }
        } while (cursor.nextSibling());
        return ret;
    }

    private void convertObjectFieldToBson(JsonNode node, JsonNodeCursor cursor, BasicDBObject ret, Path path, EntityMetadata md) {
        if (node != null) {
            if (node instanceof ObjectNode) {
                if (cursor.firstChild()) {
                    ret.append(path.tail(0), objectToBson(cursor, md));
                    cursor.parent();
                }
            } else {
                throw Error.get(ERR_INVALID_FIELD, path.toString());
            }
        }
    }

    private void convertArrayFieldToBson(JsonNode node, JsonNodeCursor cursor, BasicDBObject ret, FieldTreeNode fieldMdNode, Path path, EntityMetadata md) {
        if (node != null) {
            if (node instanceof ArrayNode) {
                if (cursor.firstChild()) {
                    ret.append(path.tail(0), arrayToBson(cursor, ((ArrayField) fieldMdNode).getElement(), md));
                    cursor.parent();
                }
            } else {
                throw Error.get(ERR_INVALID_FIELD, path.toString());
            }
        }
    }

    private void convertReferenceFieldToBson() {
        //TODO  
        throw new java.lang.UnsupportedOperationException();
    }

    /**
     * @param cursor The cursor, pointing to the first element of the array
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private List arrayToBson(JsonNodeCursor cursor, ArrayElement el, EntityMetadata md) {
        List l = new ArrayList();
        if (el instanceof SimpleArrayElement) {
            Type t = el.getType();
            do {
                Object value = toValue(t, cursor.getCurrentNode());
                l.add(value);
            } while (cursor.nextSibling());
        } else {
            do {
                JsonNode node = cursor.getCurrentNode();
                if (node == null || node instanceof NullNode) {
                    l.add(null);
                } else {
                    if (cursor.firstChild()) {
                        l.add(objectToBson(cursor, md));
                        cursor.parent();
                    } else {
                        l.add(null);
                    }
                }
            } while (cursor.nextSibling());
        }
        return l;
    }

}
