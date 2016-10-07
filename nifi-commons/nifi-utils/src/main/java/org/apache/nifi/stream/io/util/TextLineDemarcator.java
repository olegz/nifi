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
package org.apache.nifi.stream.io.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of demarcator of text lines in the provided
 * {@link InputStream}. It works similar to the {@link BufferedReader} and its
 * {@link BufferedReader#readLine()} methods except that it does not create a
 * String representing the text line and instead returns the offset info for the
 * computed text line. See {@link #nextOffsetInfo()} and
 * {@link #nextOffsetInfo(byte[])} for more details.
 * <p>
 * This class is NOT thread-safe.
 * </p>
 */
public class TextLineDemarcator {

    private final static int INIT_BUFFER_SIZE = 8192;

    private final InputStream is;

    private final int initialBufferSize;

    private byte[] buffer;

    private int index;

    private int mark;

    private long offset;

    private int bufferLength;

    /**
     * Constructs an instance of demarcator with provided {@link InputStream}
     * and default buffer size.
     */
    public TextLineDemarcator(InputStream is) {
        this(is, INIT_BUFFER_SIZE);
    }

    /**
     * Constructs an instance of demarcator with provided {@link InputStream}
     * and initial buffer size.
     */
    public TextLineDemarcator(InputStream is, int initialBufferSize) {
        if (is == null) {
            throw new IllegalArgumentException("'is' must not be null.");
        }
        if (initialBufferSize < 1) {
            throw new IllegalArgumentException("'initialBufferSize' must be > 0.");
        }
        this.is = is;
        this.initialBufferSize = initialBufferSize;
        this.buffer = new byte[initialBufferSize];
    }

    /**
     * Will compute the next <i>offset info</i> for a
     * text line (line terminated by either '\r', '\n' or '\r\n').
     * <br>
     * The <i>offset info</i> computed and returned as <code>long[]</code> consisting of
     * 4 elements <code>{startOffset, length, crlfLength, startsWithMatch}</code>.
     *  <ul>
     *    <li><i>startOffset</i> - the offset in the overall stream which represents the beginning of the text line</li>
     *    <li><i>length</i> - length of the text line including CRLF characters</li>
     *    <li><i>crlfLength</i> - the length of the CRLF. Could be either 1 (if line ends with '\n' or '\r')
     *                                          or 2 (if line ends with '\r\n').</li>
     *    <li><i>startsWithMatch</i> - value is always 1. See {@link #nextOffsetInfo(byte[])} for more info.</li>
     *  </ul>
     *
     * @return offset info as <code>long[]</code>
     */
    public long[] nextOffsetInfo() {
        return this.nextOffsetInfo(null);
    }

    /**
     * Will compute the next <i>offset info</i> for a
     * text line (line terminated by either '\r', '\n' or '\r\n').
     * <br>
     * The <i>offset info</i> computed and returned as <code>long[]</code> consisting of
     * 4 elements <code>{startOffset, length, crlfLength, startsWithMatch}</code>.
     *  <ul>
     *    <li><i>startOffset</i> - the offset in the overall stream which represents the beginning of the text line</li>
     *    <li><i>length</i> - length of the text line including CRLF characters</li>
     *    <li><i>crlfLength</i> - the length of the CRLF. Could be either 1 (if line ends with '\n' or '\r')
     *                                          or 2 (if line ends with '\r\n').</li>
     *    <li><i>startsWithMatch</i> - value is always 1 unless 'startsWith' is provided. If 'startsWith' is provided it will
     *                                          be compared to the computed text line and if matches the value
     *                                          will remain 1 otherwise it will be set to 0. </li>
     *  </ul>
     * @param startsWith - bytes
     * @return offset info as <code>long[]</code>
     */
    public long[] nextOffsetInfo(byte[] startsWith) {
        long[] offsetInfo = null;
        int lineLength = 0;
        byte[] token = null;
        lineLoop:
        while (this.bufferLength != -1) {
            if (this.index >= this.bufferLength) {
                this.fill();
            }
            if (this.bufferLength != -1) {
                int i;
                byte byteVal;
                for (i = this.index; i < this.bufferLength; i++) {
                    byteVal = this.buffer[i];
                    lineLength++;
                    int crlfLength = isEol(byteVal, i);
                    if (crlfLength > 0) {
                        i += crlfLength;
                        if (crlfLength == 2) {
                            lineLength++;
                        }
                        offsetInfo = new long[] { this.offset, lineLength, crlfLength, 1 };
                        if (startsWith != null) {
                            token = this.extractDataToken(lineLength);
                        }
                        this.index = i;
                        this.mark = this.index;
                        break lineLoop;
                    }
                }
                this.index = i;
            }
        }
        // EOF where last char(s) are not CRLF.
        if (lineLength > 0 && offsetInfo == null) {
            offsetInfo = new long[] { this.offset, lineLength, 0, 1 };
            if (startsWith != null) {
                token = this.extractDataToken(lineLength);
            }
        }
        this.offset += lineLength;

        // checks if the new line starts with 'startsWith' chars
        if (startsWith != null) {
            for (int i = 0; i < startsWith.length; i++) {
                byte sB = startsWith[i];
                if (token != null && sB != token[i]) {
                    offsetInfo[3] = 0;
                    break;
                }
            }
        }
        return offsetInfo;
    }

    /**
     *
     */
    private int isEol(byte currentByte, int currentIndex) {
        int crlfLength = 0;
        if (currentByte == '\n') {
            crlfLength = 1;
        } else if (currentByte == '\r') {
            if ((currentIndex + 1) >= this.bufferLength) {
                this.index = currentIndex + 1;
                this.fill();
            }
            currentByte = this.buffer[currentIndex + 1];
            crlfLength = currentByte == '\n' ? 2 : 1;
        }
        return crlfLength;
    }

    /**
     *
     */
    private byte[] extractDataToken(int length) {
        byte[] data = null;
        if (length > 0) {
            data = new byte[length];
            System.arraycopy(this.buffer, this.mark, data, 0, data.length);
        }
        return data;
    }

    /**
     * Will fill the current buffer from current 'index' position, expanding it
     * and or shuffling it if necessary
     */
    private void fill() {
        if (this.index >= this.buffer.length) {
            if (this.mark == 0) { // expand
                byte[] newBuff = new byte[this.buffer.length + this.initialBufferSize];
                System.arraycopy(this.buffer, 0, newBuff, 0, this.buffer.length);
                this.buffer = newBuff;
            } else { // shuffle
                int length = this.index - this.mark;
                System.arraycopy(this.buffer, this.mark, this.buffer, 0, length);
                this.index = length;
                this.mark = 0;
            }
        }

        try {
            int bytesRead;
            do {
                bytesRead = this.is.read(this.buffer, this.index, this.buffer.length - this.index);
            } while (bytesRead == 0);
            this.bufferLength = bytesRead != -1 ? this.index + bytesRead : -1;
        } catch (IOException e) {
            throw new IllegalStateException("Failed while reading InputStream", e);
        }
    }
}
