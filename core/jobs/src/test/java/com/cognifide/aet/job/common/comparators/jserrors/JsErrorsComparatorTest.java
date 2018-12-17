/**
 * AET
 *
 * Copyright (C) 2013 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.cognifide.aet.job.common.comparators.jserrors;

import static org.junit.Assert.assertEquals;

import com.cognifide.aet.communication.api.metadata.ComparatorStepResult;
import com.cognifide.aet.job.api.comparator.ComparatorProperties;
import com.cognifide.aet.job.api.datafilter.DataFilterJob;
import com.cognifide.aet.job.common.ArtifactDAOMock;
import com.cognifide.aet.job.common.comparators.AbstractComparatorTest;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JsErrorsComparatorTest extends AbstractComparatorTest {

  private ComparatorProperties comparatorProperties;

  private JsErrorsComparator tested;

  @Before
  public void setUp() throws Exception {
    artifactDaoMock = new ArtifactDAOMock(JsErrorsComparator.class);
  }

  @Test
  public void testFailed() throws Exception {
    comparatorProperties = new ComparatorProperties(TEST_COMPANY, TEST_PROJECT, null,
        "data-result.json");
    tested = new JsErrorsComparator(comparatorProperties, new ArrayList<DataFilterJob>(),
        artifactDaoMock);
    result = tested.compare();

    assertEqualsToSavedArtifact("expected-result.json");
    assertEquals(ComparatorStepResult.Status.FAILED, result.get(0).getStatus());
    assertEquals(1, result.size());
  }

  @Test
  public void testSuccess() throws Exception {
    comparatorProperties = new ComparatorProperties(TEST_COMPANY, TEST_PROJECT, null,
        "data-empty-result.json");
    tested = new JsErrorsComparator(comparatorProperties, new ArrayList<DataFilterJob>(),
        artifactDaoMock);
    result = tested.compare();

    assertEquals(null, getActual());
    assertEquals(ComparatorStepResult.Status.PASSED, result.get(0).getStatus());
    assertEquals(1, result.size());
  }
}
