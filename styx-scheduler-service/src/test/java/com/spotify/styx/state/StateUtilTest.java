/*-
 * -\-\-
 * Spotify Styx Scheduler Service
 * --
 * Copyright (C) 2016 - 2018 Spotify AB
 * --
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
 * -/-/-
 */

package com.spotify.styx.state;

import static com.spotify.styx.state.StateUtil.GLOBAL_RESOURCE_ID;
import static com.spotify.styx.testdata.TestData.RESOURCE_IDS;
import static com.spotify.styx.testdata.TestData.WORKFLOW_ID;
import static com.spotify.styx.testdata.TestData.WORKFLOW_INSTANCE;
import static com.spotify.styx.testdata.TestData.WORKFLOW_WITH_RESOURCES;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.spotify.styx.WorkflowResourceDecorator;
import com.spotify.styx.model.StyxConfig;
import com.spotify.styx.model.Workflow;
import com.spotify.styx.model.WorkflowId;
import com.spotify.styx.storage.Storage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StateUtilTest {

  @Mock private Storage storage;
  @Mock private TimeoutConfig timeoutConfig;
  @Mock private Supplier<Map<WorkflowId, Workflow>> workflowCache;

  private WorkflowResourceDecorator resourceDecorator = WorkflowResourceDecorator.NOOP;
  final private StyxConfig config = StyxConfig.newBuilder()
      .globalConcurrency(Optional.of(100L))
      .globalDockerRunnerId("")
      .build();

  @Before
  public void setUp() throws IOException {
    final RunState runState =
        RunState.create(WORKFLOW_INSTANCE, RunState.State.RUNNING, Instant.ofEpochMilli(10L));
    when(storage.readActiveStates()).thenReturn(ImmutableMap.of(WORKFLOW_INSTANCE, runState));
    when(storage.config()).thenReturn(config);
    when(timeoutConfig.ttlOf(runState.state())).thenReturn(Duration.ofMillis(2L));
    when(workflowCache.get()).thenReturn(ImmutableMap.of(WORKFLOW_ID, WORKFLOW_WITH_RESOURCES));
  }

  @Test
  public void shouldGetUsedResources() throws IOException {
    final Map<String, Long> resourcesUsageMap = StateUtil.getResourcesUsageMap(storage,
        timeoutConfig, workflowCache, Instant.ofEpochMilli(11L), resourceDecorator);
    final Map<String, Long> expectedResourcesUsageMap =
        RESOURCE_IDS.stream().collect(Collectors.toMap(Function.identity(), item -> 1L));
    expectedResourcesUsageMap.put(GLOBAL_RESOURCE_ID, 1L);

    assertThat(resourcesUsageMap, is(expectedResourcesUsageMap));
  }

  @Test
  public void shouldGetUsedResourcesWithNoRunning() throws IOException {
    final RunState runState =
        RunState.create(WORKFLOW_INSTANCE, RunState.State.QUEUED, Instant.ofEpochMilli(10L));

    when(storage.readActiveStates()).thenReturn(ImmutableMap.of(WORKFLOW_INSTANCE, runState));
    when(timeoutConfig.ttlOf(runState.state())).thenReturn(Duration.ofMillis(2L));

    final Map<String, Long> resourcesUsageMap = StateUtil.getResourcesUsageMap(storage,
        timeoutConfig, workflowCache, Instant.ofEpochMilli(11L), resourceDecorator);

    assertThat(resourcesUsageMap, is(ImmutableMap.of()));
  }

  @Test
  public void shouldGetUsedResourcesNoStates() throws IOException {
    when(storage.readActiveStates()).thenReturn(emptyMap());
    final Map<String, Long> resourcesUsageMap = StateUtil.getResourcesUsageMap(storage,
        timeoutConfig, workflowCache, Instant.ofEpochMilli(11L), resourceDecorator);
    assertThat(resourcesUsageMap, is(ImmutableMap.of()));
  }

  @Test(expected = IOException.class)
  public void shouldGetUsedResourcesStorageException() throws IOException {
    when(storage.readActiveStates()).thenThrow(new IOException());
    StateUtil.getResourcesUsageMap(storage, timeoutConfig, workflowCache,
        Instant.ofEpochMilli(11L), resourceDecorator);
  }

  @Test
  public void shouldConsumeResource() {
    assertTrue(StateUtil.isConsumingResources(RunState.State.PREPARE));
    assertTrue(StateUtil.isConsumingResources(RunState.State.SUBMITTING));
    assertTrue(StateUtil.isConsumingResources(RunState.State.SUBMITTED));
    assertTrue(StateUtil.isConsumingResources(RunState.State.RUNNING));
  }

  @Test
  public void shouldNotConsumeResource() {
    assertFalse(StateUtil.isConsumingResources(RunState.State.NEW));
    assertFalse(StateUtil.isConsumingResources(RunState.State.QUEUED));
    assertFalse(StateUtil.isConsumingResources(RunState.State.TERMINATED));
    assertFalse(StateUtil.isConsumingResources(RunState.State.FAILED));
    assertFalse(StateUtil.isConsumingResources(RunState.State.ERROR));
    assertFalse(StateUtil.isConsumingResources(RunState.State.DONE));
  }
}