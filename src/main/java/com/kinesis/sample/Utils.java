/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kinesis.sample;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;

public class Utils {
    private static final Random RANDOM = new Random();
    
   
    public static String randomExplicitHashKey() {
        return new BigInteger(128, RANDOM).toString(10);
    }
   
    public static ByteBuffer generateData(long sequenceNumber, int totalLen) {
        StringBuilder sb = new StringBuilder();
        sb.append(Long.toString(sequenceNumber));
        sb.append(" ");
        while (sb.length() < totalLen) {
            sb.append("a");
        }
        try {
            return ByteBuffer.wrap(sb.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
