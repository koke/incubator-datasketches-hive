/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.datasketches.hive.cpc;

import java.util.Arrays;

import org.apache.datasketches.cpc.CpcSketch;
import org.apache.datasketches.memory.Memory;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;

// This class implements functionality common to DataToSketch and UnionSketch

abstract class SketchEvaluator extends GenericUDAFEvaluator {

  static final int DEFAULT_LG_K = 11;

  protected static final String LG_K_FIELD = "lgK";
  protected static final String SEED_FIELD = "seed";
  protected static final String SKETCH_FIELD = "sketch";

  protected PrimitiveObjectInspector inputInspector_;
  protected PrimitiveObjectInspector lgKInspector_;
  protected PrimitiveObjectInspector seedInspector_;
  protected StructObjectInspector intermediateInspector_;

  @Override
  public Object terminatePartial(final @SuppressWarnings("deprecation") AggregationBuffer buf)
      throws HiveException {
    final State state = (State) buf;
    final CpcSketch intermediate = state.getResult();
    if (intermediate == null) { return null; }
    final byte[] bytes = intermediate.toByteArray();
    return Arrays.asList(
      new IntWritable(state.getLgK()),
      new LongWritable(state.getSeed()),
      new BytesWritable(bytes)
    );
  }

  @Override
  public void merge(final @SuppressWarnings("deprecation") AggregationBuffer buf, final Object data)
      throws HiveException {
    if (data == null) { return; }
    final UnionState state = (UnionState) buf;
    if (!state.isInitialized()) {
      initializeState(state, data);
    }
    final BytesWritable serializedSketch = (BytesWritable) intermediateInspector_.getStructFieldData(
        data, intermediateInspector_.getStructFieldRef(SKETCH_FIELD));
    state.update(CpcSketch.heapify(Memory.wrap(serializedSketch.getBytes()), state.getSeed()));
  }

  private void initializeState(final UnionState state, final Object data) {
    final int lgK = ((IntWritable) intermediateInspector_.getStructFieldData(
        data, intermediateInspector_.getStructFieldRef(LG_K_FIELD))).get();
    final long seed = ((LongWritable) intermediateInspector_.getStructFieldData(
        data, intermediateInspector_.getStructFieldRef(SEED_FIELD))).get();
    state.init(lgK, seed);
  }

  @Override
  public Object terminate(final @SuppressWarnings("deprecation") AggregationBuffer buf)
      throws HiveException {
    final State state = (State) buf;
    if (state == null) { return null; }
    final CpcSketch result = state.getResult();
    if (result == null) { return null; }
    return new BytesWritable(result.toByteArray());
  }

  @Override
  public void reset(@SuppressWarnings("deprecation") final AggregationBuffer buf)
      throws HiveException {
    final State state = (State) buf;
    state.reset();
  }

}
