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
package com.redhat.lightblue.metadata.parser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.redhat.lightblue.metadata.MetadataConstants;
import com.redhat.lightblue.metadata.DataStore;
import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.metadata.types.DefaultTypes;
import com.redhat.lightblue.util.Error;
import com.redhat.lightblue.util.test.AbstractJsonSchemaTest;

public class JSONMetadataParserTest extends AbstractJsonSchemaTest {

    JsonNodeFactory factory = new JsonNodeFactory(true);

    private JSONMetadataParser parser;

    @Before
    public void setup() {
        Extensions<JsonNode> extensions = new Extensions<>();
        extensions.addDefaultExtensions();
        extensions.registerDataStoreParser("empty", new DataStoreParser<JsonNode>() {

            @Override
            public DataStore parse(String name, MetadataParser<JsonNode> p, JsonNode node) {
                if (!"empty".equals(name)) {
                    throw Error.get(MetadataConstants.ERR_ILL_FORMED_METADATA, name);
                }

                DataStore ds = new DataStore() {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public String getType() {
                        return "empty";
                    }
                };
                return ds;
            }

            @Override
            public void convert(MetadataParser<JsonNode> p, JsonNode emptyNode, DataStore ds) {
                // nothing to do
            }
        });
        parser = new JSONMetadataParser(extensions, new DefaultTypes(), factory);
    }

    @After
    public void tearDown() {
        parser = null;
    }

    private void testResource(String resource) throws IOException, JSONException, ProcessingException {
        // verify json is schema compliant
        runValidJsonTest("json-schema/metadata/metadata.json", resource);

        JsonNode object = loadJsonNode(resource);

        // json to java
        EntityMetadata em = parser.parseEntityMetadata(object);

        // verify got something
        Assert.assertNotNull(em);

        // java back to json
        JsonNode converted = parser.convert(em);

        String original = loadResource(resource);
        String before = object.toString();
        String after = converted.toString();
        JSONAssert.assertEquals(original, before, false);
        JSONAssert.assertEquals(original, after, false);
    }

    @Test
    public void fullObjectEverythingNoHooks() throws IOException, ParseException, JSONException, ProcessingException {
        testResource("JSONMetadataParserTest-object-everything-no-hooks.json");
    }

//    @Test hooks not implemented yet
//    public void fullObjectEverything() throws IOException, ParseException, JSONException {
//        testResource("JSONMetadataParserTest-object-everything.json");
//    }
    @Test
    public void getStringProperty() {
        String name = "name";
        String value = "value";
        ObjectNode parent = new ObjectNode(factory);
        parent.put(name, value);

        Assert.assertEquals(value, parser.getStringProperty(parent, name));
    }

    @Test
    public void getObjectProperty() {
        String name = "name";
        String value = "value";
        ObjectNode parent = new ObjectNode(factory);
        parent.put(name, value);
        parent.put(name + "1", value + "1");

        JsonNode object = parser.getObjectProperty(parent, name);

        Assert.assertNotNull("couldn't find node by name", object);
        Assert.assertEquals(value, object.textValue());
    }

    @Test
    public void getValuePropertyNumber() {
        String name = "name";
        Integer value = 1;
        ObjectNode parent = new ObjectNode(factory);
        parent.put(name, value);

        Object object = parser.getValueProperty(parent, name);

        Assert.assertTrue("expected instanceof Number", object instanceof Number);

        Assert.assertEquals(value.intValue(), ((Number) object).intValue());
    }

    @Test
    public void getValuePropertyObject() {
        String name = "name";
        String value = "value";
        ObjectNode parent = new ObjectNode(factory);
        parent.put(name, value);

        Object object = parser.getValueProperty(parent, name);

        Assert.assertTrue("expected instanceof String", object instanceof String);

        Assert.assertEquals(value, (String) object);
    }

    @Test
    public void getStringList() {
        String name = "x";
        int count = 3;
        ObjectNode parent = new ObjectNode(factory);
        ArrayNode array = factory.arrayNode();
        parent.put(name, array);

        for (int i = 0; i < count; i++) {
            array.add(i);
        }

        List<String> l = parser.getStringList(parent, name);

        Assert.assertNotNull(l);
        Assert.assertEquals(count, l.size());

        for (int i = 0; i < count; i++) {
            Assert.assertEquals("value at index wrong: " + i, String.valueOf(i), l.get(i));
        }
    }

    @Test
    public void getObjectList() {
        String name = "x";
        int count = 3;
        ObjectNode parent = new ObjectNode(factory);
        ArrayNode array = factory.arrayNode();
        parent.put(name, array);

        for (int i = 0; i < count; i++) {
            array.add(i);
        }

        List<JsonNode> l = parser.getObjectList(parent, name);

        Assert.assertNotNull(l);
        Assert.assertEquals(count, l.size());

        for (int i = 0; i < count; i++) {
            Assert.assertEquals("value at index wrong: " + i, i, l.get(i).intValue());
        }
    }

    @Test
    public void getChildNames() {
        List<String> childNames = new ArrayList<>();
        childNames.add("bob");
        childNames.add("jerry");
        childNames.add("hammer");
        childNames.add("suzy");

        ObjectNode parent = new ObjectNode(factory);

        for (String s : childNames) {
            parent.put(s, "value");
        }

        Set<String> s = parser.getChildNames(parent);

        Assert.assertNotNull(s);
        Assert.assertEquals(childNames.size(), s.size());

        for (int i = 0; i < childNames.size(); i++) {
            s.remove(childNames.get(i));
        }

        Assert.assertTrue("not all child names were removed..", s.isEmpty());
    }

    @Test
    public void newNode() {
        JsonNode x = parser.newNode();

        Assert.assertNotNull(x);
    }

    @Test
    public void putString() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        String value = "bar";

        parser.putString(parent, name, value);

        JsonNode x = parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value, x.textValue());
    }

    @Test
    public void putObject() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        Object value = new ObjectNode(factory);

        parser.putObject(parent, name, value);

        JsonNode x = parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertTrue("expected instanceof ObjectNode", x instanceof ObjectNode);
        Assert.assertEquals(value, x);
    }

    @Test
    public void putValueBoolean() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        Boolean value = Boolean.TRUE;

        parser.putValue(parent, name, value);

        JsonNode x = (JsonNode) parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value.booleanValue(), x.booleanValue());
    }

    @Test
    public void putValueBigDecimal() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        BigDecimal value = new BigDecimal("213.55");

        parser.putValue(parent, name, value);

        JsonNode x = (JsonNode) parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value, x.decimalValue());
    }

    @Test
    public void putValueBigInteger() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        BigInteger value = new BigInteger("123444");

        parser.putValue(parent, name, value);

        JsonNode x = (JsonNode) parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value, x.bigIntegerValue());
    }

    @Test
    public void putValueDouble() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        Double value = new Double("12928.222");

        parser.putValue(parent, name, value);

        JsonNode x = (JsonNode) parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value.doubleValue(), x.doubleValue(), 0.001);
    }

    @Test
    public void putValueFloat() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        Float value = new Float("123.222");

        parser.putValue(parent, name, value);

        JsonNode x = (JsonNode) parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value.floatValue(), x.floatValue(), 0.001);
    }

    @Test
    public void putValueInteger() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        Integer value = 123444;

        parser.putValue(parent, name, value);

        JsonNode x = (JsonNode) parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value.intValue(), x.intValue());
    }

    @Test
    public void putValueLong() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        Long value = 1272722l;

        parser.putValue(parent, name, value);

        JsonNode x = (JsonNode) parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value.longValue(), x.longValue());
    }

    @Test
    public void putValueSohrt() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        Short value = 123;

        parser.putValue(parent, name, value);

        JsonNode x = (JsonNode) parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value.shortValue(), x.shortValue());
    }

    @Test
    public void putValueString() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        String value = "bar";

        parser.putValue(parent, name, value);

        JsonNode x = (JsonNode) parent.get(name);

        Assert.assertNotNull(x);
        Assert.assertEquals(value, x.textValue());
    }

    @Test
    public void newArrayField() {
        ObjectNode parent = new ObjectNode(factory);
        String name = "foo";
        Object array = parser.newArrayField(parent, name);

        Assert.assertNotNull(array);
        Assert.assertEquals(array, parent.get(name));
    }

    @Test
    public void addStringToArray() {
        String value = "bar";
        ArrayNode array = new ArrayNode(factory);

        Assert.assertEquals(0, array.size());

        parser.addStringToArray(array, value);

        Assert.assertEquals(1, array.size());
        Assert.assertEquals(value, array.get(0).textValue());
    }

    @Test
    public void addObjectToArray() {
        JsonNode value = new TextNode("asdf");
        ArrayNode array = new ArrayNode(factory);

        Assert.assertEquals(0, array.size());

        parser.addObjectToArray(array, value);

        Assert.assertEquals(1, array.size());
        Assert.assertEquals(value, array.get(0));
    }

}
