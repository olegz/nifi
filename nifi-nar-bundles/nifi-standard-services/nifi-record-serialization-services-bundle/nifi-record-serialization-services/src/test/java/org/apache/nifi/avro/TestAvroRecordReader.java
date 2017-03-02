/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.avro;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.junit.Test;

public class TestAvroRecordReader {

    @Test
    public void testDataTypes() throws IOException, MalformedRecordException {
        final List<Field> accountFields = new ArrayList<>();
        accountFields.add(new Field("accountId", Schema.create(Type.LONG), null, null));
        accountFields.add(new Field("accountName", Schema.create(Type.STRING), null, null));
        final Schema accountSchema = Schema.createRecord("account", null, null, false);
        accountSchema.setFields(accountFields);

        final List<Field> catFields = new ArrayList<>();
        catFields.add(new Field("catTailLength", Schema.create(Type.INT), null, null));
        catFields.add(new Field("catName", Schema.create(Type.STRING), null, null));
        final Schema catSchema = Schema.createRecord("cat", null, null, false);
        catSchema.setFields(catFields);

        final List<Field> dogFields = new ArrayList<>();
        dogFields.add(new Field("dogTailLength", Schema.create(Type.INT), null, null));
        dogFields.add(new Field("dogName", Schema.create(Type.STRING), null, null));
        final Schema dogSchema = Schema.createRecord("dog", null, null, false);
        dogSchema.setFields(dogFields);

        final List<Field> fields = new ArrayList<>();
        fields.add(new Field("name", Schema.create(Type.STRING), null, null));
        fields.add(new Field("age", Schema.create(Type.INT), null, null));
        fields.add(new Field("balance", Schema.create(Type.DOUBLE), null, null));
        fields.add(new Field("rate", Schema.create(Type.FLOAT), null, null));
        fields.add(new Field("debt", Schema.create(Type.BOOLEAN), null, null));
        fields.add(new Field("nickname", Schema.create(Type.NULL), null, null));
        fields.add(new Field("binary", Schema.create(Type.BYTES), null, null));
        fields.add(new Field("fixed", Schema.createFixed("fixed", null, null, 5), null, null));
        fields.add(new Field("map", Schema.createMap(Schema.create(Type.STRING)), null, null));
        fields.add(new Field("array", Schema.createArray(Schema.create(Type.LONG)), null, null));
        fields.add(new Field("account", accountSchema, null, null));
        fields.add(new Field("desiredbalance", Schema.createUnion( // test union of NULL and other type with no value
            Arrays.asList(Schema.create(Type.NULL), Schema.create(Type.DOUBLE))),
            null, null));
        fields.add(new Field("dreambalance", Schema.createUnion( // test union of NULL and other type with a value
            Arrays.asList(Schema.create(Type.NULL), Schema.create(Type.DOUBLE))),
            null, null));
        fields.add(new Field("favAnimal", Schema.createUnion(Arrays.asList(catSchema, dogSchema)), null, null));
        fields.add(new Field("otherFavAnimal", Schema.createUnion(Arrays.asList(catSchema, dogSchema)), null, null));

        final Schema schema = Schema.createRecord("record", null, null, false);
        schema.setFields(fields);

        final byte[] source;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final Map<String, String> map = new HashMap<>();
        map.put("greeting", "hello");
        map.put("salutation", "good-bye");

        final DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
        try (final DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
            final DataFileWriter<GenericRecord> writer = dataFileWriter.create(schema, baos)) {

            final GenericRecord record = new GenericData.Record(schema);
            record.put("name", "John");
            record.put("age", 33);
            record.put("balance", 1234.56D);
            record.put("rate", 0.045F);
            record.put("debt", false);
            record.put("binary", ByteBuffer.wrap("binary".getBytes(StandardCharsets.UTF_8)));
            record.put("fixed", new GenericData.Fixed(Schema.create(Type.BYTES), "fixed".getBytes(StandardCharsets.UTF_8)));
            record.put("map", map);
            record.put("array", Arrays.asList(1L, 2L));
            record.put("dreambalance", 10_000_000.00D);

            final GenericRecord accountRecord = new GenericData.Record(accountSchema);
            accountRecord.put("accountId", 83L);
            accountRecord.put("accountName", "Checking");
            record.put("account", accountRecord);

            final GenericRecord catRecord = new GenericData.Record(catSchema);
            catRecord.put("catTailLength", 1);
            catRecord.put("catName", "Meow");
            record.put("otherFavAnimal", catRecord);

            final GenericRecord dogRecord = new GenericData.Record(dogSchema);
            dogRecord.put("dogTailLength", 14);
            dogRecord.put("dogName", "Fido");
            record.put("favAnimal", dogRecord);

            writer.append(record);
        }

        source = baos.toByteArray();

        try (final InputStream in = new ByteArrayInputStream(source)) {
            final AvroRecordReader reader = new AvroRecordReader(in);
            final RecordSchema recordSchema = reader.getSchema();
            assertEquals(15, recordSchema.getFieldCount());

            assertEquals(RecordFieldType.STRING, recordSchema.getDataType("name").get().getFieldType());
            assertEquals(RecordFieldType.INT, recordSchema.getDataType("age").get().getFieldType());
            assertEquals(RecordFieldType.DOUBLE, recordSchema.getDataType("balance").get().getFieldType());
            assertEquals(RecordFieldType.FLOAT, recordSchema.getDataType("rate").get().getFieldType());
            assertEquals(RecordFieldType.BOOLEAN, recordSchema.getDataType("debt").get().getFieldType());
            assertEquals(RecordFieldType.RECORD, recordSchema.getDataType("nickname").get().getFieldType());
            assertEquals(RecordFieldType.ARRAY, recordSchema.getDataType("binary").get().getFieldType());
            assertEquals(RecordFieldType.ARRAY, recordSchema.getDataType("fixed").get().getFieldType());
            assertEquals(RecordFieldType.RECORD, recordSchema.getDataType("map").get().getFieldType());
            assertEquals(RecordFieldType.ARRAY, recordSchema.getDataType("array").get().getFieldType());
            assertEquals(RecordFieldType.RECORD, recordSchema.getDataType("account").get().getFieldType());
            assertEquals(RecordFieldType.DOUBLE, recordSchema.getDataType("desiredbalance").get().getFieldType());
            assertEquals(RecordFieldType.DOUBLE, recordSchema.getDataType("dreambalance").get().getFieldType());
            assertEquals(RecordFieldType.RECORD, recordSchema.getDataType("favAnimal").get().getFieldType());
            assertEquals(RecordFieldType.RECORD, recordSchema.getDataType("otherFavAnimal").get().getFieldType());

            final Object[] values = reader.nextRecord(recordSchema);
            assertEquals(15, values.length);
            assertEquals("John", values[0]);
            assertEquals(33, values[1]);
            assertEquals(1234.56D, values[2]);
            assertEquals(0.045F, values[3]);
            assertEquals(false, values[4]);
            assertEquals(null, values[5]);
            assertArrayEquals("binary".getBytes(StandardCharsets.UTF_8), (byte[]) values[6]);
            assertArrayEquals("fixed".getBytes(StandardCharsets.UTF_8), (byte[]) values[7]);
            assertEquals(map, values[8]);
            assertArrayEquals(new Object[] {1L, 2L}, (Object[]) values[9]);

            final Map<String, Object> accountValues = new HashMap<>();
            accountValues.put("accountName", "Checking");
            accountValues.put("accountId", 83L);
            assertEquals(accountValues, values[10]);

            assertNull(values[11]);
            assertEquals(10_000_000.0D, values[12]);

            final Map<String, Object> dogMap = new HashMap<>();
            dogMap.put("dogName", "Fido");
            dogMap.put("dogTailLength", 14);
            assertEquals(dogMap, values[13]);

            final Map<String, Object> catMap = new HashMap<>();
            catMap.put("catName", "Meow");
            catMap.put("catTailLength", 1);
            assertEquals(catMap, values[14]);
        }
    }

    public static enum Status {
        GOOD, BAD;
    }
}
