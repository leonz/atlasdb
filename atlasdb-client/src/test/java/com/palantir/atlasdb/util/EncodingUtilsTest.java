/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.json.simple.parser.ParseException;
import org.junit.Test;

import com.palantir.atlasdb.protos.generated.TableMetadataPersistence;
import com.palantir.atlasdb.table.description.TableDefinition;
import com.palantir.atlasdb.table.description.TableMetadata;
import com.palantir.atlasdb.table.description.ValueType;
import com.palantir.atlasdb.transaction.api.ConflictHandler;

public class EncodingUtilsTest {
    private static final long TEST_TS = 123;

    private static final TableMetadata METADATA = new TableDefinition() {
        {
            rowName();
            rowComponent("blob", ValueType.BLOB);
            columns();
            column("bar", "b", ValueType.BLOB);
            conflictHandler(ConflictHandler.IGNORE_ALL);
            sweepStrategy(TableMetadataPersistence.SweepStrategy.NOTHING);
        }
    }.toTableMetadata();
    private static final String METADATA_AS_HEX_STRING = "0x0a120a0e0a04626c6f621004180120013001180012140a120a016212"
            + "036261721a06080418012003200118012040280030004000480058006001";

    @Test
    public void transformTimestampInvolutes() {
        assertThat(EncodingUtils.transformTimestamp(EncodingUtils.transformTimestamp(TEST_TS))).isEqualTo(TEST_TS);
    }

    @Test
    public void transformTimestampInvertsBits() {
        long expected_ts = -124;
        assertThat(EncodingUtils.transformTimestamp(TEST_TS)).isEqualTo(expected_ts);
    }

    // This test incidentally also tests that the JsonSerialization of SweepProgress did not change
    @Test
    public void hexToStringDecodesJsonBlobCorrectly() throws ParseException {
        String jsonBlob = "0x7b227461626c65526566223a7b226e616d657370616365223a7b226e616d65223a22666f6f227d2c227461626"
                + "c656e616d65223a22626172227d2c227374617274526f77223a2241514944222c227374617274436f6c756d6e223a226457"
                + "35316332566b222c227374616c6556616c75657344656c65746564223a31302c2263656c6c547350616972734578616d696"
                + "e6564223a3230302c226d696e696d756d537765707454696d657374616d70223a31323334357d";

        String expectedJson = "{\"tableRef\":{\"namespace\":{\"name\":\"foo\"},\"tablename\":\"bar\"},"
                + "\"startRow\":\"AQID\",\"startColumn\":\"dW51c2Vk\",\"staleValuesDeleted\":10,"
                + "\"cellTsPairsExamined\":200,\"minimumSweptTimestamp\":12345}";

        assertThat(EncodingUtils.hexToString(jsonBlob)).isEqualTo(expectedJson);
    }

    @Test
    public void stringToHexEncodesSimpleRowNameCorrectly() {
        String simpleRowName = "s";
        String expectedOutput = "0x73";

        assertThat(EncodingUtils.stringToHex(simpleRowName)).isEqualTo(expectedOutput);
    }

    @Test
    public void hexToMetadataTest() {
        assertThat(EncodingUtils.hexToMetadata(METADATA_AS_HEX_STRING)).isEqualTo(METADATA);
    }

    @Test
    public void metadataToHexTest() {
        assertThat(EncodingUtils.metadataToHex(METADATA)).isEqualTo(METADATA_AS_HEX_STRING);
    }
}