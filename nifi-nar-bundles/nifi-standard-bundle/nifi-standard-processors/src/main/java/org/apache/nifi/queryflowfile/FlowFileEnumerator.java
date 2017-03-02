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

package org.apache.nifi.queryflowfile;

import java.io.IOException;
import java.io.InputStream;

import org.apache.calcite.linq4j.Enumerator;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.serialization.RowRecordReader;
import org.apache.nifi.serialization.RowRecordReaderFactory;
import org.apache.nifi.serialization.record.RecordSchema;

public class FlowFileEnumerator<InternalType> implements Enumerator<Object> {
    private final ProcessSession session;
    private final FlowFile flowFile;
    private final ComponentLog logger;
    private final RowRecordReaderFactory recordParserFactory;
    private final int[] fields;

    private InputStream rawIn;
    private Object currentRecord;
    private RecordSchema schema;
    private RowRecordReader recordParser;

    public FlowFileEnumerator(final ProcessSession session, final FlowFile flowFile, final ComponentLog logger, final RowRecordReaderFactory parserFactory,
            final RecordSchema schema, final int[] fields) {
        this.session = session;
        this.flowFile = flowFile;
        this.recordParserFactory = parserFactory;
        this.schema = schema;
        this.logger = logger;
        this.fields = fields;
        reset();
    }

    @Override
    public Object current() {
        return currentRecord;
    }

    @Override
    public boolean moveNext() {
        currentRecord = null;
        while (currentRecord == null) {
            try {
                currentRecord = filterColumns(recordParser.nextRecord(schema));
                break;
            } catch (final IOException e) {
                logger.error("Failed to read next record in stream for " + flowFile + ". Assuming end of stream.", e);
                currentRecord = null;
                break;
            } catch (final MalformedRecordException mre) {
                logger.error("Failed to parse record in stream for " + flowFile + ". Will skip record and continue reading", mre);
            }
        }

        if (currentRecord == null) {
            // If we are out of data, close the InputStream. We do this because
            // Calcite does not necessarily call our close() method.
            close();
        }
        return (currentRecord != null);
    }

    private Object filterColumns(final Object[] row) {
        // If we want no fields or if the row is null, just return null
        if (fields == null || row == null) {
            return row;
        }

        // If we want only a single field, then Calcite is going to expect us to return
        // the actual value, NOT a 1-element array of values.
        if (fields.length == 1) {
            final int desiredCellIndex = fields[0];
            return row[desiredCellIndex];
        }

        // Create a new Object array that contains only the desired fields.
        if (row.length <= fields.length) {
            return row;
        }

        final Object[] filtered = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            final int indexToKeep = fields[i];
            filtered[i] = row[indexToKeep];
        }

        return filtered;
    }

    @Override
    public void reset() {
        if (rawIn != null) {
            try {
                rawIn.close();
            } catch (final Exception e) {
                logger.warn("Could not close FlowFile's input due to " + e, e);
            }
        }

        rawIn = session.read(flowFile);

        try {
            recordParser = recordParserFactory.createRecordReader(rawIn, logger);
        } catch (final MalformedRecordException | IOException e) {
            throw new ProcessException("Failed to reset stream", e);
        }
    }

    @Override
    public void close() {
        if (recordParser != null) {
            try {
                recordParser.close();
            } catch (final Exception e) {
                logger.warn("Failed to close decorated source for " + flowFile, e);
            }
        }

        try {
            rawIn.close();
        } catch (final Exception e) {
            logger.warn("Failed to close InputStream for " + flowFile, e);
        }
    }
}
