/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.table.description;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import static com.palantir.atlasdb.AtlasDbConstants.SCHEMA_V2_TABLE_NAME;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.keyvalue.api.Namespace;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;

public class SchemaTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private static final String TEST_PACKAGE = "package";
    private static final String TEST_TABLE_NAME = "TestTable";
    private static final String TEST_INDEX_NAME = TEST_TABLE_NAME + IndexDefinition.IndexType.ADDITIVE.getIndexSuffix();
    private static final String TEST_PATH = TEST_PACKAGE + "/" + TEST_TABLE_NAME + "Table.java";
    private static final TableReference TABLE_REF = TableReference.createWithEmptyNamespace(TEST_TABLE_NAME);
    private static final TableReference INDEX_TABLE_REF = TableReference.createWithEmptyNamespace(TEST_INDEX_NAME);
    private static final String EXPECTED_FILES_FOLDER_PATH = "src/integrationInput/java";

    @Test
    public void testRendersGuavaOptionalsByDefault() throws IOException {
        Schema schema = new Schema("Table", TEST_PACKAGE, Namespace.DEFAULT_NAMESPACE);
        schema.addTableDefinition("TableName", getSimpleTableDefinition(TABLE_REF));
        schema.renderTables(testFolder.getRoot());
        assertThat(readFileIntoString(testFolder.getRoot(), TEST_PATH),
                allOf(
                        containsString("import com.google.common.base.Optional"),
                        containsString("{@link Optional}"),
                        containsString("Optional.absent")));
    }

    @Test
    public void testRendersGuavaOptionalsWhenRequested() throws IOException {
        Schema schema = new Schema("Table", TEST_PACKAGE, Namespace.DEFAULT_NAMESPACE, OptionalType.GUAVA);
        schema.addTableDefinition("TableName", getSimpleTableDefinition(TABLE_REF));
        schema.renderTables(testFolder.getRoot());
        assertThat(readFileIntoString(testFolder.getRoot(), TEST_PATH),
                allOf(
                        containsString("import com.google.common.base.Optional"),
                        not(containsString("import java.util.Optional"))));
    }

    @Test
    public void testRenderJava8OptionalsWhenRequested() throws IOException {
        Schema schema = new Schema("Table", TEST_PACKAGE, Namespace.DEFAULT_NAMESPACE, OptionalType.JAVA8);
        schema.addTableDefinition("TableName", getSimpleTableDefinition(TABLE_REF));
        schema.renderTables(testFolder.getRoot());
        assertThat(readFileIntoString(testFolder.getRoot(), TEST_PATH),
                allOf(
                        not(containsString("import com.google.common.base.Optional")),
                        containsString("import java.util.Optional")));
    }

    @Test
    public void testIgnoreTableNameLengthFlag() {
        Schema schema = new Schema("Table", TEST_PACKAGE, Namespace.EMPTY_NAMESPACE);
        schema.ignoreTableNameLengthChecks();
        String longTableName = String.join("", Collections.nCopies(1000, "x"));
        TableReference tableRef = TableReference.createWithEmptyNamespace(longTableName);
        schema.addTableDefinition(longTableName, getSimpleTableDefinition(tableRef));
    }

    @Test
    public void testLongTableNameLengthFailsCassandra() {
        Schema schema = new Schema("Table", TEST_PACKAGE, Namespace.EMPTY_NAMESPACE);
        int longLengthCassandra = AtlasDbConstants.CASSANDRA_TABLE_NAME_CHAR_LIMIT + 1;
        String longTableName = String.join("", Collections.nCopies(longLengthCassandra, "x"));
        TableReference tableRef = TableReference.createWithEmptyNamespace(longTableName);
        List<CharacterLimitType> kvsList = new ArrayList<CharacterLimitType>();
        kvsList.add(CharacterLimitType.CASSANDRA);
        assertThatThrownBy(() ->
                schema.addTableDefinition(longTableName, getSimpleTableDefinition(tableRef)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(getErrorMessage(longTableName, kvsList));
    }

    @Test
    public void testLongTableNameLengthFailsBoth() {
        Schema schema = new Schema("Table", TEST_PACKAGE, Namespace.EMPTY_NAMESPACE);
        String longTableName = String.join("", Collections.nCopies(1000, "x"));
        TableReference tableRef = TableReference.createWithEmptyNamespace(longTableName);
        List<CharacterLimitType> kvsList = new ArrayList<CharacterLimitType>();
        kvsList.add(CharacterLimitType.CASSANDRA);
        kvsList.add(CharacterLimitType.POSTGRES);
        assertThatThrownBy(() ->
                schema.addTableDefinition(longTableName, getSimpleTableDefinition(tableRef)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(getErrorMessage(longTableName, kvsList));
    }

    @Test
    public void testLongTableNameLengthFailsNamespace() {
        // If namespace is non-empty, internal table name length is |namespace| + |tableName| + 2
        Schema schema = new Schema("Table", TEST_PACKAGE, Namespace.DEFAULT_NAMESPACE);
        String longTableName = String.join("", Collections.nCopies(40, "x"));
        TableReference tableRef = TableReference.create(Namespace.DEFAULT_NAMESPACE, longTableName);
        List<CharacterLimitType> kvsList = new ArrayList<CharacterLimitType>();
        kvsList.add(CharacterLimitType.CASSANDRA);
        assertThatThrownBy(() ->
                schema.addTableDefinition(longTableName, getSimpleTableDefinition(tableRef)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(getErrorMessage(longTableName, kvsList));
    }

    @Test
    // If you are intentionally making Table API changes, please manually regenerate the ApiTestSchema
    // and copy the new files to the ${EXPECTED_FILES_FOLDER_PATH} folder.
    public void checkAgainstAccidentalTableAPIChanges() throws IOException {
        // TODO (amarzoca): Add tests for schemas that use more of the rendering features (Triggers, StreamStores, etc)
        Schema schema = ApiTestSchema.getSchema();
        schema.renderTables(testFolder.getRoot());

        List<String> generatedTestTables = ApiTestSchema.getSchema().getAllTables().stream()
                .map(entry -> entry.getTablename() + "Table")
                .collect(Collectors.toList());

        checkIfFilesAreTheSame(generatedTestTables);
    }

    @Test
    public void checkAgainstAccidentalTableV2APIChanges() throws IOException {
        Schema schema = ApiTestSchema.getSchema();
        schema.renderTables(testFolder.getRoot());

        List<String> generatedTestTables = ApiTestSchema.getSchema().getTableDefinitions().values()
                .stream()
                .filter(TableDefinition::hasV2TableEnabled)
                .map(entry -> entry.getJavaTableName() + SCHEMA_V2_TABLE_NAME)
                .collect(Collectors.toList());

        checkIfFilesAreTheSame(generatedTestTables);
    }

    @Test
    public void testLongIndexNameLengthFailsCassandra() {
        Schema schema = new Schema("Table", TEST_PACKAGE, Namespace.EMPTY_NAMESPACE);
        int longLengthCassandra = AtlasDbConstants.CASSANDRA_TABLE_NAME_CHAR_LIMIT;
        String longTableName = String.join("", Collections.nCopies(longLengthCassandra, "x"));
        TableReference tableRef = TableReference.createWithEmptyNamespace(longTableName);
        List<CharacterLimitType> kvsList = new ArrayList<>();
        kvsList.add(CharacterLimitType.CASSANDRA);
        schema.addTableDefinition(longTableName, getSimpleTableDefinition(tableRef));
        assertThatThrownBy(() -> {
            IndexDefinition id = new IndexDefinition(IndexDefinition.IndexType.ADDITIVE);
            id.onTable(longTableName);
            schema.addIndexDefinition(longTableName, id);
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        getErrorMessage(longTableName + IndexDefinition.IndexType.ADDITIVE.getIndexSuffix(), kvsList));
    }

    @Test
    public void v2SchemaTablesCanBeGeneratedWithAllRowAndColumnComponents() {
        Schema schema = new Schema("Table", TEST_PACKAGE, Namespace.EMPTY_NAMESPACE);
        schema.addTableDefinition(TEST_TABLE_NAME, new TableDefinition() {{
            enableV2Table();
            ignoreHotspottingChecks();
            rowName();
            Arrays.stream(ValueType.values())
                    .filter(valueType -> valueType != ValueType.STRING && valueType != ValueType.BLOB)
                    .forEach(valueType -> rowComponent(valueType.name(), valueType));
            rowComponent("BLOB", ValueType.BLOB);
            columns();
            Arrays.stream(ValueType.values())
                    .forEach(valueType -> column(valueType.name(), valueType.name() + "-long", valueType));
        }});

        schema.validate();
    }

    @Test
    public void canAddDeprecatedTablesAndIndexes() {
        Namespace namespace = Namespace.create("namespace");
        Schema schema = new Schema("TableWithDeprecations", TEST_PACKAGE, namespace);
        schema.addTableDefinition(TEST_TABLE_NAME, getSimpleTableDefinition(TABLE_REF));
        schema.addDeprecatedTables("anOldTable");

        schema.validate();
        assertThat(schema.getDeprecatedTables()).containsOnly(
                TableReference.create(namespace, "anOldTable"));
    }

    @Test
    public void cannotAddDeprecatedTablesOrIndexesThatConflictWithCurrentTables() {
        Namespace namespace = Namespace.create("namespace");
        Schema schema = new Schema("TableWithDeprecations", TEST_PACKAGE, namespace);
        schema.addTableDefinition(TEST_TABLE_NAME, getSimpleTableDefinition(TABLE_REF));
        schema.addDeprecatedTables(TEST_TABLE_NAME);

        assertThatExceptionOfType(SafeIllegalStateException.class)
                .isThrownBy(schema::validate)
                .withMessageStartingWith("A deprecated table cannot also be part of your schema. "
                        + "Check logs for any unsafe table names.")
                .satisfies(e -> assertThat(e.getArgs()).contains(UnsafeArg.of(
                        "invalidDeprecatedTables_unsafe",
                        ImmutableSet.of(TableReference.create(namespace, TEST_TABLE_NAME)))));
    }

    private void checkIfFilesAreTheSame(List<String> generatedTestTables) {
        generatedTestTables.forEach(tableName -> {
            String generatedFilePath =
                    String.format("com/palantir/atlasdb/table/description/generated/%s.java", tableName);

            File expectedFile = new File(EXPECTED_FILES_FOLDER_PATH, generatedFilePath);
            File actualFile = new File(testFolder.getRoot(), generatedFilePath);
            assertThat(actualFile).hasSameContentAs(expectedFile);
        });
    }

    private String readFileIntoString(File baseDir, String path) throws IOException {
        return new String(Files.toByteArray(new File(baseDir, path)), StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"checkstyle:Indentation", "checkstyle:RightCurly"})
    private TableDefinition getSimpleTableDefinition(TableReference tableRef) {
        return new TableDefinition() {{
            javaTableName(tableRef.getTablename());
            rowName();
            rowComponent("rowName", ValueType.STRING);
            columns();
            column("col1", "1", ValueType.VAR_LONG);
        }};
    }

    private String getErrorMessage(String tableName, List<CharacterLimitType> kvsExceeded) {
        return String.format("Internal table name %s is too long, known to exceed character limits for "
                        + "the following KVS: %s. If using a table prefix, please ensure that the concatenation "
                        + "of the prefix with the internal table name is below the KVS limit. "
                        + "If running only against a different KVS, set the ignoreTableNameLength flag.",
                tableName, StringUtils.join(kvsExceeded, ", "));
    }

}
