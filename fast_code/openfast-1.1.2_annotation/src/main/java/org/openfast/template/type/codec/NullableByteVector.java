/*
The contents of this file are subject to the Mozilla Public License
Version 1.1 (the "License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at
http://www.mozilla.org/MPL/

Software distributed under the License is distributed on an "AS IS"
basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
License for the specific language governing rights and limitations
under the License.

The Original Code is OpenFAST.

The Initial Developer of the Original Code is The LaSalle Technology
Group, LLC.  Portions created by The LaSalle Technology Group, LLC
are Copyright (C) The LaSalle Technology Group, LLC. All Rights Reserved.

Contributor(s): Jacob Northey <jacob@lasalletech.com>
                Craig Otis <cotis@lasalletech.com>
 */
package org.openfast.template.type.codec;

import java.io.IOException;
import java.io.InputStream;
import org.openfast.ByteVectorValue;
import org.openfast.Global;
import org.openfast.IntegerValue;
import org.openfast.ScalarValue;
import org.openfast.error.FastConstants;

public class NullableByteVector extends NotStopBitEncodedTypeCodec {
    private static final long serialVersionUID = 1L;

    /**
     * Reads in a stream of data and stores it to a ByteVectorValue object
     * 
     * @param in
     *            The InputStream to be decoded
     * @return Returns a new ByteVectorValue object with the data stream as an
     *         array
     */
    public ScalarValue decode(InputStream in) {
        ScalarValue decode = TypeCodec.NULLABLE_UNSIGNED_INTEGER.decode(in);
        if (decode == null)
            return null;
        int length = ((ScalarValue) decode).toInt();
        byte[] encoding = new byte[length];
        for (int i = 0; i < length; i++)
            try {
                int nextByte = in.read();
                if (nextByte < 0) {
                    Global.handleError(FastConstants.END_OF_STREAM, "The end of the input stream has been reached.");
                    return null; // short circuit if global error handler does not throw exception
                }
                encoding[i] = (byte) nextByte;
            } catch (IOException e) {
                Global.handleError(FastConstants.IO_ERROR, "An error occurred while decoding a nullable byte vector.", e);
                return null; // short circuit if global error handler does not throw exception
            }
        return new ByteVectorValue(encoding);
    }

    /**
     * Takes a ScalarValue object, and converts it to a byte array
     * 
     * @param value
     *            The ScalarValue to be encoded
     * @return Returns a byte array of the passed object
     */
    public byte[] encodeValue(ScalarValue value) {
        if (value.isNull())
            return TypeCodec.NULLABLE_UNSIGNED_INTEGER.encodeValue(ScalarValue.NULL);
        ByteVectorValue byteVectorValue = (ByteVectorValue) value;
        byte[] length = TypeCodec.NULLABLE_UNSIGNED_INTEGER.encode(new IntegerValue(byteVectorValue.value.length));
        int lengthSize = length.length;
        byte[] encoding = new byte[byteVectorValue.value.length + lengthSize];
        System.arraycopy(length, 0, encoding, 0, lengthSize);
        System.arraycopy(byteVectorValue.value, 0, encoding, lengthSize, byteVectorValue.value.length);
        return encoding;
    }

    /**
     * 
     * @return Returns a default ByteVectorValue object
     */
    public ScalarValue getDefaultValue() {
        return new ByteVectorValue(new byte[] {});
    }

    /**
     * @return Returns a new ByteVectorValue object with the passed value
     */
    public ScalarValue fromString(String value) {
        return new ByteVectorValue(value.getBytes());
    }

    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass();
    }
}
