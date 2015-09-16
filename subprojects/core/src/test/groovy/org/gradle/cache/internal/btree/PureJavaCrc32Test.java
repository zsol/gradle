/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.cache.internal.btree;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;
import java.util.zip.CRC32;

/**
 * test copied from Apache Hadoop
 */
public class PureJavaCrc32Test {
    private final CRC32 theirs = new CRC32();
    private final PureJavaCrc32 ours = new PureJavaCrc32();

    @Test
    public void testCorrectness() throws Exception {
        checkSame();

        theirs.update(104);
        ours.update(104);
        checkSame();

        checkOnBytes(new byte[]{40, 60, 97, -70}, false);

        checkOnBytes("hello world!".getBytes("UTF-8"), false);

        for (int i = 0; i < 10000; i++) {
            byte randomBytes[] = new byte[new Random().nextInt(2048)];
            new Random().nextBytes(randomBytes);
            checkOnBytes(randomBytes, false);
        }

    }

    private void checkOnBytes(byte[] bytes, boolean print) {
        theirs.reset();
        ours.reset();
        checkSame();

        for (int i = 0; i < bytes.length; i++) {
            ours.update(bytes[i]);
            theirs.update(bytes[i]);
            checkSame();
        }

        if (print) {
            System.out.println("theirs:\t" + Long.toHexString(theirs.getValue())
                + "\nours:\t" + Long.toHexString(ours.getValue()));
        }

        theirs.reset();
        ours.reset();

        ours.update(bytes, 0, bytes.length);
        theirs.update(bytes, 0, bytes.length);
        if (print) {
            System.out.println("theirs:\t" + Long.toHexString(theirs.getValue())
                + "\nours:\t" + Long.toHexString(ours.getValue()));
        }

        checkSame();

        if (bytes.length >= 10) {
            ours.update(bytes, 5, 5);
            theirs.update(bytes, 5, 5);
            checkSame();
        }
    }

    private void checkSame() {
        Assert.assertEquals(theirs.getValue(), ours.getValue());
    }

}
