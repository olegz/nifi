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

package org.apache.nifi.csv;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.serialization.DataType;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.serialization.RecordField;
import org.apache.nifi.serialization.RecordFieldType;
import org.apache.nifi.serialization.RecordSchema;
import org.apache.nifi.serialization.RowRecordReader;
import org.apache.nifi.serialization.SimpleRecordSchema;

import au.com.bytecode.opencsv.CSVReader;

public class CSVRecordReader implements RowRecordReader {
    private final ComponentLog logger;
    private final CSVReader reader;
    private final String[] firstLine;
    private final Map<String, DataType> fieldTypeOverrides;


    public CSVRecordReader(final InputStream in, final ComponentLog logger, final Map<String, DataType> fieldTypeOverrides) throws IOException {
        this.logger = logger;
        reader = new CSVReader(new InputStreamReader(new BufferedInputStream(in)));
        firstLine = reader.readNext();
        this.fieldTypeOverrides = fieldTypeOverrides;
    }

    @Override
    public Object[] nextRecord(final RecordSchema schema) throws IOException, MalformedRecordException {
        while (true) {
            final String[] line = reader.readNext();
            if (line == null) {
                return null;
            }

            final List<DataType> fieldTypes = schema.getDataTypes();
            if (fieldTypes.size() != line.length) {
                logger.warn("Found record with incorrect number of fields. Expected {} but found {}; skipping record", new Object[] {fieldTypes.size(), line.length});
                continue;
            }

            try {
                if (fieldTypes.size() == 1) {
                    return new Object[] {convert(fieldTypes.get(0), line[0].trim())};
                } else {
                    final Object[] objects = new Object[fieldTypes.size()];
                    for (int i = 0; i < fieldTypes.size(); i++) {
                        objects[i] = convert(fieldTypes.get(i), line[i].trim());
                    }
                    return objects;
                }
            } catch (final Exception e) {
                throw new MalformedRecordException("Found invalid CSV record", e);
            }
        }
    }

    @Override
    public RecordSchema getSchema() {
        final List<RecordField> recordFields = new ArrayList<>();
        for (final String element : firstLine) {

            final String name = element.trim();
            final DataType dataType;

            final DataType overriddenDataType = fieldTypeOverrides.get(name);
            if (overriddenDataType != null) {
                dataType = overriddenDataType;
            } else {
                dataType = new DataType(RecordFieldType.STRING);
            }

            final RecordField field = new RecordField(name, dataType);
            recordFields.add(field);
        }

        if (recordFields.isEmpty()) {
            recordFields.add(new RecordField("line", new DataType(RecordFieldType.STRING)));
        }

        return new SimpleRecordSchema(recordFields);
    }

    protected Object convert(final DataType dataType, final String value) {
        if (dataType == null) {
            return value;
        }

        switch (dataType.getFieldType()) {
            case BOOLEAN:
                if (value.length() == 0) {
                    return null;
                }
                return Boolean.parseBoolean(value);
            case BYTE:
                if (value.length() == 0) {
                    return null;
                }
                return Byte.parseByte(value);
            case SHORT:
                if (value.length() == 0) {
                    return null;
                }
                return Short.parseShort(value);
            case INT:
                if (value.length() == 0) {
                    return null;
                }
                return Integer.parseInt(value);
            case LONG:
            case BIGINT:
                if (value.length() == 0) {
                    return null;
                }
                return Long.parseLong(value);
            case FLOAT:
                if (value.length() == 0) {
                    return null;
                }
                return Float.parseFloat(value);
            case DOUBLE:
                if (value.length() == 0) {
                    return null;
                }
                return Double.parseDouble(value);
            case DATE:
                if (value.length() == 0) {
                    return null;
                }
                try {
                    final Date date = new SimpleDateFormat(dataType.getFormat()).parse(value);
                    return new java.sql.Date(date.getTime());
                } catch (final ParseException e) {
                    // TODO: Throw MalformedRecordException instead of logging?
                    logger.warn("Found invalid value for DATE field: " + value + " does not match expected format of "
                        + dataType.getFormat() + "; will substitute a NULL value for this field");
                    return null;
                }
            case TIME:
                if (value.length() == 0) {
                    return null;
                }
                try {
                    final Date date = new SimpleDateFormat(dataType.getFormat()).parse(value);
                    return new java.sql.Time(date.getTime());
                } catch (final ParseException e) {
                    logger.warn("Found invalid value for TIME field: " + value + " does not match expected format of "
                        + dataType.getFormat() + "; will substitute a NULL value for this field");
                    return null;
                }
            case TIMESTAMP:
                if (value.length() == 0) {
                    return null;
                }
                try {
                    final Date date = new SimpleDateFormat(dataType.getFormat()).parse(value);
                    return new java.sql.Timestamp(date.getTime());
                } catch (final ParseException e) {
                    logger.warn("Found invalid value for TIMESTAMP field: " + value + " does not match expected format of "
                        + dataType.getFormat() + "; will substitute a NULL value for this field");
                    return null;
                }
            case STRING:
            default:
                return value;
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}