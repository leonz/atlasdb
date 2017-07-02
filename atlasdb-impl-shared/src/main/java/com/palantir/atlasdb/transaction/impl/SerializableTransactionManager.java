/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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
package com.palantir.atlasdb.transaction.impl;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.palantir.atlasdb.cleaner.Cleaner;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.transaction.api.AtlasDbConstraintCheckingMode;
import com.palantir.atlasdb.transaction.api.TransactionReadSentinelBehavior;
import com.palantir.atlasdb.transaction.service.TransactionService;
import com.palantir.lock.LockClient;
import com.palantir.lock.RemoteLockService;
import com.palantir.lock.impl.LegacyTimelockService;
import com.palantir.lock.v2.LockTokenV2;
import com.palantir.lock.v2.TimelockService;
import com.palantir.timestamp.TimestampService;

public class SerializableTransactionManager extends SnapshotTransactionManager {

    public SerializableTransactionManager(KeyValueService keyValueService,
            TimestampService timestampService,
            LockClient lockClient,
            RemoteLockService lockService,
            TransactionService transactionService,
            Supplier<AtlasDbConstraintCheckingMode> constraintModeSupplier,
            ConflictDetectionManager conflictDetectionManager,
            SweepStrategyManager sweepStrategyManager,
            Cleaner cleaner) {
        this(keyValueService,
                timestampService,
                lockClient,
                lockService,
                transactionService,
                constraintModeSupplier,
                conflictDetectionManager,
                sweepStrategyManager,
                cleaner,
                false);
    }

    public SerializableTransactionManager(KeyValueService keyValueService,
            TimestampService timestampService,
            LockClient lockClient,
            RemoteLockService lockService,
            TransactionService transactionService,
            Supplier<AtlasDbConstraintCheckingMode> constraintModeSupplier,
            ConflictDetectionManager conflictDetectionManager,
            SweepStrategyManager sweepStrategyManager,
            Cleaner cleaner,
            boolean allowHiddenTableAccess) {
        this(
                keyValueService,
                new LegacyTimelockService(timestampService, lockService, lockClient),
                lockService,
                transactionService,
                constraintModeSupplier,
                conflictDetectionManager,
                sweepStrategyManager,
                cleaner,
                allowHiddenTableAccess);
    }

    public SerializableTransactionManager(KeyValueService keyValueService,
            TimelockService timelockService,
            RemoteLockService lockService,
            TransactionService transactionService,
            Supplier<AtlasDbConstraintCheckingMode> constraintModeSupplier,
            ConflictDetectionManager conflictDetectionManager,
            SweepStrategyManager sweepStrategyManager,
            Cleaner cleaner,
            boolean allowHiddenTableAccess) {
        super(
                keyValueService,
                timelockService,
                lockService,
                transactionService,
                constraintModeSupplier,
                conflictDetectionManager,
                sweepStrategyManager,
                cleaner,
                allowHiddenTableAccess);
    }

    @Override
    protected SnapshotTransaction createTransaction(long immutableTimestamp,
            Supplier<Long> startTimestampSupplier,
            ImmutableList<LockTokenV2> lockTokens,
            PreCommitValidation preCommitValidation) {
        return new SerializableTransaction(
                keyValueService,
                timelockService,
                transactionService,
                cleaner,
                startTimestampSupplier,
                conflictDetectionManager,
                sweepStrategyManager,
                immutableTimestamp,
                lockTokens,
                preCommitValidation,
                constraintModeSupplier.get(),
                cleaner.getTransactionReadTimeoutMillis(),
                TransactionReadSentinelBehavior.THROW_EXCEPTION,
                allowHiddenTableAccess,
                timestampValidationReadCache);
    }

}

