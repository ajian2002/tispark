/*
 *
 * Copyright 2017 PingCAP, Inc.
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
 *
 */

package com.pingcap.tikv.util;

import com.google.common.base.Preconditions;
import com.pingcap.tikv.exception.GrpcException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tikv.common.log.SlowLog;
import org.tikv.common.util.BackOffFunction.BackOffFuncType;

public class ConcreteBackOffer implements BackOffer {
  private static final Logger logger = LoggerFactory.getLogger(ConcreteBackOffer.class);
  private final int maxSleep;
  private final Map<BackOffFunction.BackOffFuncType, BackOffFunction> backOffFunctionMap;
  private final List<Exception> errors;
  private int totalSleep;

  private final Long clusterId;

  private final org.tikv.common.util.ConcreteBackOffer upstreamConcreteBackOffer;

  private ConcreteBackOffer(int maxSleep, long clusterId) {
    this.clusterId = clusterId;
    this.upstreamConcreteBackOffer =
        org.tikv.common.util.ConcreteBackOffer.newCustomBackOff(maxSleep);
    Preconditions.checkArgument(maxSleep >= 0, "Max sleep time cannot be less than 0.");
    this.maxSleep = maxSleep;
    this.errors = Collections.synchronizedList(new ArrayList<>());
    this.backOffFunctionMap = new ConcurrentHashMap<>();
  }

  private ConcreteBackOffer(ConcreteBackOffer source) {
    this.upstreamConcreteBackOffer =
        org.tikv.common.util.ConcreteBackOffer.newCustomBackOff(source.maxSleep);
    this.maxSleep = source.maxSleep;
    this.totalSleep = source.totalSleep;
    this.errors = source.errors;
    this.clusterId = source.clusterId;
    this.backOffFunctionMap = source.backOffFunctionMap;
  }

  public static ConcreteBackOffer newCustomBackOff(int maxSleep, long clusterId) {
    return new ConcreteBackOffer(maxSleep, clusterId);
  }

  public static ConcreteBackOffer newCustomBackOff(int maxSleep) {
    return newCustomBackOff(maxSleep, 0);
  }

  public static ConcreteBackOffer newScannerNextMaxBackOff() {
    return new ConcreteBackOffer(SCANNER_NEXT_MAX_BACKOFF, 0);
  }

  public static ConcreteBackOffer newBatchGetMaxBackOff() {
    return new ConcreteBackOffer(BATCH_GET_MAX_BACKOFF, 0);
  }

  public static ConcreteBackOffer newCopNextMaxBackOff() {
    return newCopNextMaxBackOff(0);
  }

  public static ConcreteBackOffer newCopNextMaxBackOff(long clusterId) {
    return new ConcreteBackOffer(COP_NEXT_MAX_BACKOFF, clusterId);
  }

  public static ConcreteBackOffer newGetBackOff(long clusterId) {
    return new ConcreteBackOffer(GET_MAX_BACKOFF, clusterId);
  }

  public static ConcreteBackOffer newRawKVBackOff() {
    return new ConcreteBackOffer(RAWKV_MAX_BACKOFF, 0);
  }

  public static ConcreteBackOffer newRawKVBackOff(long clusterId) {
    return new ConcreteBackOffer(RAWKV_MAX_BACKOFF, clusterId);
  }

  public static ConcreteBackOffer newTsoBackOff(long clusterId) {
    return new ConcreteBackOffer(TSO_MAX_BACKOFF, clusterId);
  }

  public static ConcreteBackOffer create(BackOffer source) {
    return new ConcreteBackOffer(((ConcreteBackOffer) source));
  }

  /**
   * Creates a back off func which implements exponential back off with optional jitters according
   * to different back off strategies. See http://www.awsarchitectureblog.com/2015/03/backoff.html
   */
  private BackOffFunction createBackOffFunc(BackOffFunction.BackOffFuncType funcType) {
    BackOffFunction backOffFunction = null;
    switch (funcType) {
      case BoUpdateLeader:
        backOffFunction = BackOffFunction.create(1, 10, BackOffStrategy.NoJitter);
        break;
      case BoTxnLockFast:
        backOffFunction = BackOffFunction.create(100, 3000, BackOffStrategy.EqualJitter);
        break;
      case BoServerBusy:
        backOffFunction = BackOffFunction.create(2000, 10000, BackOffStrategy.EqualJitter);
        break;
      case BoRegionMiss:
        backOffFunction = BackOffFunction.create(100, 500, BackOffStrategy.NoJitter);
        break;
      case BoTxnLock:
        backOffFunction = BackOffFunction.create(200, 3000, BackOffStrategy.EqualJitter);
        break;
      case BoPDRPC:
        backOffFunction = BackOffFunction.create(500, 3000, BackOffStrategy.EqualJitter);
        break;
      case BoTiKVRPC:
        backOffFunction = BackOffFunction.create(100, 2000, BackOffStrategy.EqualJitter);
        break;
      case BoTxnNotFound:
        backOffFunction = BackOffFunction.create(2, 500, BackOffStrategy.NoJitter);
        break;
      case BoCheckHealth:
        backOffFunction = BackOffFunction.create(100, 600, BackOffStrategy.EqualJitter);
        break;
    }
    return backOffFunction;
  }

  @Override
  public void doBackOff(BackOffFunction.BackOffFuncType funcType, Exception err) {
    doBackOffWithMaxSleep(funcType, -1, err);
  }

  @Override
  public void doBackOffWithMaxSleep(
      BackOffFunction.BackOffFuncType funcType, long maxSleepMs, Exception err) {
    BackOffFunction backOffFunction =
        backOffFunctionMap.computeIfAbsent(funcType, this::createBackOffFunc);

    // Back off will be done here
    totalSleep = Math.toIntExact(totalSleep + backOffFunction.doBackOff(maxSleepMs));
    logger.debug(
        String.format(
            "%s, retry later(totalSleep %dms, maxSleep %dms)",
            err.getMessage(), totalSleep, maxSleep));
    errors.add(err);
    if (maxSleep > 0 && totalSleep >= maxSleep) {
      StringBuilder errMsg =
          new StringBuilder(
              String.format("BackOffer.maxSleep %dms is exceeded, errors:", maxSleep));
      for (int i = 0; i < errors.size(); i++) {
        Exception curErr = errors.get(i);
        // Print only last 3 errors for non-DEBUG log levels.
        if (logger.isDebugEnabled() || i >= errors.size() - 3) {
          errMsg.append("\n").append(i).append(".").append(curErr.toString());
        }
      }
      logger.warn(errMsg.toString());
      // Use the last backoff type to generate an exception
      throw new GrpcException("retry is exhausted.", err);
    }
  }

  @Override
  public void doBackOff(BackOffFuncType funcType, Exception err) {
    doBackOff(ConverterUpstream.reConvertBackOffFunctionType(funcType), err);
  }

  @Override
  public void checkTimeout() {
    upstreamConcreteBackOffer.checkTimeout();
  }

  @Override
  public boolean canRetryAfterSleep(BackOffFuncType funcType) {
    return upstreamConcreteBackOffer.canRetryAfterSleep(funcType);
  }

  @Override
  public void doBackOffWithMaxSleep(BackOffFuncType funcType, long maxSleepMs, Exception err) {
    doBackOffWithMaxSleep(
        ConverterUpstream.reConvertBackOffFunctionType(funcType), maxSleepMs, err);
  }

  @Override
  public SlowLog getSlowLog() {
    return upstreamConcreteBackOffer.getSlowLog();
  }

  @Override
  public Long getClusterId() {
    // https://github.com/tikv/client-java/pull/558
    // TODO: to implement
    return 0L;
  }
}
